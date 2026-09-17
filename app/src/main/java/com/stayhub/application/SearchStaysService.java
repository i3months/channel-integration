package com.stayhub.application;

import com.stayhub.domain.FailureReason;
import com.stayhub.domain.Offer;
import com.stayhub.domain.RoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import com.stayhub.domain.port.StayMappingRepository;
import com.stayhub.domain.port.SupplierAdapter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 통합 검색. 매핑에서 공급사별 숙소 코드를 꺼내 50개 단위로 나누고, 공급사를 병렬로 조회해 합친다.
 *
 * 병렬 호출은 WebClient 기반 Mono 로 만들고, 결과를 모으는 이 메서드에서 한 번만 block 한다.
 * 공급사 안에서는 동시 청크 수를 제한해 호출 한도 초과를 피하고, 공급사 사이에는 제한이 없다.
 * 전체 시간 예산이 지나면 도착한 청크까지만 쓰고, 오지 않은 청크는 TIMEOUT 실패로 드러낸다.
 */
public class SearchStaysService {

    private static final Logger log = LoggerFactory.getLogger(SearchStaysService.class);
    private static final String BUDGET_EXCEEDED = "search budget exceeded";

    private final StayMappingRepository repository;
    private final SupplierRegistry registry;
    private final Duration searchBudget;
    private final int chunkSize;
    private final int chunkConcurrency;

    public SearchStaysService(StayMappingRepository repository, SupplierRegistry registry,
            Duration searchBudget, int chunkSize, int chunkConcurrency) {
        this.repository = repository;
        this.registry = registry;
        this.searchBudget = searchBudget;
        this.chunkSize = chunkSize;
        this.chunkConcurrency = chunkConcurrency;
    }

    public SearchResult search(StaySearchCriteria criteria) {
        String searchId = UUID.randomUUID().toString();
        MDC.put("searchId", searchId);
        long started = System.nanoTime();
        try {
            SearchResult result = doSearch(criteria);
            log.info("event=search_completed searchId={} nights={} itemCount={} failureCount={} elapsedMs={}",
                    searchId, criteria.nights(), result.items().size(), result.failures().size(),
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
            return result;
        } finally {
            MDC.remove("searchId");
        }
    }

    private SearchResult doSearch(StaySearchCriteria criteria) {
        List<Stay> activeStays = repository.findAllActiveStays();
        Map<SupplierCode, List<Stay>> staysBySupplier = activeStays.stream()
                .sorted(Comparator.comparingLong(Stay::id))
                .collect(Collectors.groupingBy(Stay::supplier, () -> new EnumMap<>(SupplierCode.class), Collectors.toList()));

        List<SearchFailure> failures = new ArrayList<>();
        Map<SupplierCode, Integer> chunkCounts = new EnumMap<>(SupplierCode.class);
        List<Flux<ChunkResult>> calls = new ArrayList<>();

        for (SupplierAdapter adapter : registry.all()) {
            SupplierCode supplier = adapter.supplierCode();
            List<Stay> stays = staysBySupplier.getOrDefault(supplier, List.of());
            if (stays.isEmpty()) {
                failures.add(new SearchFailure(supplier, FailureReason.NO_MAPPING, "no active stays for supplier"));
                continue;
            }
            List<List<String>> chunks = Chunks.split(stays.stream().map(Stay::supplierHotelCode).toList(), chunkSize);
            chunkCounts.put(supplier, chunks.size());
            calls.add(Flux.range(0, chunks.size())
                    .flatMap(index -> adapter.fetchAvailability(chunks.get(index), criteria)
                            .map(result -> new ChunkResult(supplier, index, result)), chunkConcurrency));
        }

        List<ChunkResult> arrived = Flux.merge(calls)
                .take(searchBudget)
                .collectList()
                .block();

        failures.addAll(aggregateFailures(chunkCounts, arrived == null ? List.of() : arrived));
        failures.sort(Comparator.comparing(SearchFailure::supplier));

        List<Offer> offers = arrived == null ? List.of() : arrived.stream()
                .filter(chunk -> chunk.result() instanceof SupplierResult.Success<List<Offer>>)
                .flatMap(chunk -> ((SupplierResult.Success<List<Offer>>) chunk.result()).value().stream())
                .toList();

        return new SearchResult(criteria, toItems(offers, activeStays, criteria), failures);
    }

    private List<SearchFailure> aggregateFailures(Map<SupplierCode, Integer> chunkCounts, List<ChunkResult> arrived) {
        Map<SupplierCode, Map<Integer, ChunkResult>> arrivedBySupplier = new EnumMap<>(SupplierCode.class);
        for (ChunkResult chunk : arrived) {
            arrivedBySupplier.computeIfAbsent(chunk.supplier(), code -> new HashMap<>()).put(chunk.index(), chunk);
        }

        List<SearchFailure> failures = new ArrayList<>();
        chunkCounts.forEach((supplier, total) -> {
            Map<Integer, ChunkResult> received = arrivedBySupplier.getOrDefault(supplier, Map.of());
            int failed = 0;
            SupplierResult.Failure<?> first = null;
            for (int index = 0; index < total; index++) {
                ChunkResult chunk = received.get(index);
                SupplierResult.Failure<?> failure = chunk == null
                        ? new SupplierResult.Failure<>(FailureReason.TIMEOUT, BUDGET_EXCEEDED)
                        : chunk.result() instanceof SupplierResult.Failure<?> f ? f : null;
                if (failure != null) {
                    failed++;
                    if (first == null) {
                        first = failure;
                    }
                }
            }
            if (first != null) {
                failures.add(new SearchFailure(supplier, first.reason(),
                        failed + "/" + total + " chunks failed: " + first.message()));
            }
        });
        return failures;
    }

    private List<SearchItem> toItems(List<Offer> offers, List<Stay> activeStays, StaySearchCriteria criteria) {
        Map<String, Stay> stayByKey = new HashMap<>();
        activeStays.forEach(stay -> stayByKey.put(key(stay.supplier(), stay.supplierHotelCode()), stay));

        Map<Long, List<RoomType>> roomTypesByStay =
                repository.findActiveRoomTypesByStayIds(activeStays.stream().map(Stay::id).toList());

        List<SearchItem> items = new ArrayList<>();
        for (Offer offer : offers) {
            Stay stay = stayByKey.get(key(offer.supplier(), offer.supplierHotelCode()));
            RoomType roomType = stay == null ? null : roomTypesByStay.getOrDefault(stay.id(), List.of()).stream()
                    .filter(candidate -> candidate.supplierRoomTypeCode().equals(offer.supplierRoomTypeCode()))
                    .findFirst()
                    .orElse(null);
            if (stay == null || roomType == null) {
                log.warn("event=offer_dropped supplier={} code={}/{} reason=no_active_mapping",
                        offer.supplier(), offer.supplierHotelCode(), offer.supplierRoomTypeCode());
                continue;
            }
            if (criteria.guests() > roomType.maxOccupancy() || offer.availableRooms() <= 0) {
                continue;
            }
            items.add(new SearchItem(stay.id(), stay.name(), roomType.id(), roomType.name(), roomType.maxOccupancy(),
                    offer.availableRooms(), offer.supplier(), offer.price()));
        }

        items.sort(Comparator.comparing((SearchItem item) -> item.supplier().name())
                .thenComparingLong(SearchItem::stayId)
                .thenComparingLong(SearchItem::roomTypeId));
        return items;
    }

    private static String key(SupplierCode supplier, String supplierHotelCode) {
        return supplier.name() + "|" + supplierHotelCode;
    }

    private record ChunkResult(SupplierCode supplier, int index, SupplierResult<List<Offer>> result) {
    }
}
