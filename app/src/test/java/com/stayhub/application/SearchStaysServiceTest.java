package com.stayhub.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.persistence.StayMappingRepositoryImpl;
import com.stayhub.domain.DailyRate;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.Offer;
import com.stayhub.domain.Price;
import com.stayhub.domain.RoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;

/**
 * 시간 예산 처리가 깨지면 응답 없는 공급사를 영원히 기다리게 된다.
 * 빌드가 멈추지 않고 실패로 드러나도록 테스트마다 제한 시간을 둔다.
 */
@DataJpaTest
@Import(StayMappingRepositoryImpl.class)
@Timeout(10)
class SearchStaysServiceTest {

    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);
    private static final Duration BUDGET = Duration.ofSeconds(5);

    @Autowired
    StayMappingRepositoryImpl repository;

    private FakeSupplierAdapter supplierA;
    private FakeSupplierAdapter supplierB;

    @BeforeEach
    void setUp() {
        supplierA = new FakeSupplierAdapter(SupplierCode.A);
        supplierB = new FakeSupplierAdapter(SupplierCode.B);
    }

    private SearchStaysService service(Duration budget, int chunkSize, int concurrency) {
        return new SearchStaysService(repository, new SupplierRegistry(List.of(supplierA, supplierB)),
                budget, chunkSize, concurrency);
    }

    private SearchStaysService service() {
        return service(BUDGET, 50, 4);
    }

    private RoomType seed(SupplierCode supplier, String hotelCode, String stayName, String roomCode, String roomName) {
        Stay stay = repository.upsertStay(supplier, hotelCode, stayName);
        return repository.upsertRoomType(stay.id(), roomCode, roomName, 2);
    }

    private void seedMockData() {
        seed(SupplierCode.A, "A-10023", "Riverside Hotel Seoul", "DLX-TWN", "Deluxe Twin");
        seed(SupplierCode.A, "A-10044", "Namsan Garden Stay", "STD-DBL", "Standard Double");
        seed(SupplierCode.B, "B77120", "Riverside Hotel Seoul", "R-401", "Deluxe Twin Room");
    }

    private static Offer offer(SupplierCode supplier, String hotelCode, String roomCode, int available, long total) {
        return new Offer(supplier, hotelCode, roomCode, available, new Price(total, "KRW", false, 3, null));
    }

    private static Mono<SupplierResult<List<Offer>>> success(Offer... offers) {
        return Mono.just(SupplierResult.success(List.of(offers)));
    }

    private static Mono<SupplierResult<List<Offer>>> failure(FailureReason reason, String message) {
        return Mono.just(SupplierResult.failure(reason, message));
    }

    private void supplierAReturnsMockData() {
        var riversidePrice = new Price(429_000L, "KRW", false, 3, List.of(
                new DailyRate(LocalDate.of(2026, 9, 1), 120_000L, 12_000L),
                new DailyRate(LocalDate.of(2026, 9, 2), 150_000L, 15_000L),
                new DailyRate(LocalDate.of(2026, 9, 3), 120_000L, 12_000L)));
        supplierA.availabilityReturns((codes, criteria) -> success(
                new Offer(SupplierCode.A, "A-10023", "DLX-TWN", 1, riversidePrice),
                offer(SupplierCode.A, "A-10044", "STD-DBL", 0, 294_800L)));
    }

    private void supplierBReturnsMockData() {
        supplierB.availabilityReturns((codes, criteria) -> success(new Offer(SupplierCode.B, "B77120", "R-401", 1,
                new Price(452_000L, "KRW", true, 3, null))));
    }

    @Test
    void T50_두_공급사_모두_성공이면_예약_가능한_2건을_A_다음_B_순서로_반환한다() {
        seedMockData();
        supplierAReturnsMockData();
        supplierBReturnsMockData();

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.criteria()).isEqualTo(SEP_1_TO_4);
        assertThat(result.failures()).isEmpty();
        assertThat(result.items()).hasSize(2);

        SearchItem a = result.items().get(0);
        Stay riversideA = repository.findStay(SupplierCode.A, "A-10023").orElseThrow();
        assertThat(a.supplier()).isEqualTo(SupplierCode.A);
        assertThat(a.stayId()).isEqualTo(riversideA.id());
        assertThat(a.stayName()).isEqualTo("Riverside Hotel Seoul");
        assertThat(a.roomTypeId()).isEqualTo(repository.findRoomType(riversideA.id(), "DLX-TWN").orElseThrow().id());
        assertThat(a.roomTypeName()).isEqualTo("Deluxe Twin");
        assertThat(a.maxOccupancy()).isEqualTo(2);
        assertThat(a.availableRooms()).isEqualTo(1);
        assertThat(a.price().totalAmount()).isEqualTo(429_000L);
        assertThat(a.price().daily()).hasSize(3);

        SearchItem b = result.items().get(1);
        assertThat(b.supplier()).isEqualTo(SupplierCode.B);
        assertThat(b.roomTypeName()).isEqualTo("Deluxe Twin Room");
        assertThat(b.price().totalAmount()).isEqualTo(452_000L);
        assertThat(b.price().breakfastIncluded()).isTrue();
        assertThat(b.price().daily()).isNull();
    }

    @Test
    void SR02_공급사에는_active_숙소_코드를_stayId_순서로_보낸다() {
        seedMockData();

        service().search(SEP_1_TO_4);

        assertThat(supplierA.availabilityCalls).containsExactly(List.of("A-10023", "A-10044"));
        assertThat(supplierB.availabilityCalls).containsExactly(List.of("B77120"));
    }

    @Test
    void T51_A_가_실패하고_B_가_성공하면_B_결과와_A_실패를_함께_반환한다() {
        seedMockData();
        supplierA.availabilityReturns((codes, criteria) -> failure(FailureReason.UNAVAILABLE, "503 SERVICE_UNAVAILABLE"));
        supplierBReturnsMockData();

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).extracting(SearchItem::supplier).containsExactly(SupplierCode.B);
        assertThat(result.failures()).containsExactly(new SearchFailure(SupplierCode.A, FailureReason.UNAVAILABLE,
                "1/1 chunks failed: 503 SERVICE_UNAVAILABLE"));
    }

    @Test
    void T52_둘_다_실패하면_결과는_비고_실패가_2건() {
        seedMockData();
        supplierA.availabilityReturns((codes, criteria) -> failure(FailureReason.UNAVAILABLE, "503"));
        supplierB.availabilityReturns((codes, criteria) -> failure(FailureReason.TIMEOUT, "response timeout after 3000ms"));

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).isEmpty();
        assertThat(result.failures()).extracting(SearchFailure::supplier, SearchFailure::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(SupplierCode.A, FailureReason.UNAVAILABLE),
                        org.assertj.core.groups.Tuple.tuple(SupplierCode.B, FailureReason.TIMEOUT));
    }

    @Test
    void T53_예약_가능_객실_수가_0_인_Offer_는_제외한다() {
        seedMockData();
        supplierAReturnsMockData();

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).extracting(SearchItem::stayName).doesNotContain("Namsan Garden Stay");
    }

    @Test
    void T54_매핑에_없는_숙소_코드의_Offer_는_제외하고_나머지는_유지한다() {
        seedMockData();
        supplierA.availabilityReturns((codes, criteria) -> success(
                offer(SupplierCode.A, "A-10023", "DLX-TWN", 1, 100),
                offer(SupplierCode.A, "A-99999", "DLX-TWN", 1, 100)));

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).extracting(SearchItem::stayName).containsExactly("Riverside Hotel Seoul");
    }

    @Test
    void SR02_비활성_객실_타입이나_매핑에_없는_객실_타입의_Offer_는_제외한다() {
        seedMockData();
        Stay riverside = repository.findStay(SupplierCode.A, "A-10023").orElseThrow();
        repository.upsertRoomType(riverside.id(), "OLD-ROOM", "Old Room", 2);
        repository.deactivateRoomTypesNotIn(riverside.id(), Set.of("DLX-TWN"));
        supplierA.availabilityReturns((codes, criteria) -> success(
                offer(SupplierCode.A, "A-10023", "DLX-TWN", 1, 100),
                offer(SupplierCode.A, "A-10023", "OLD-ROOM", 1, 100),
                offer(SupplierCode.A, "A-10023", "UNKNOWN", 1, 100)));

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).extracting(SearchItem::roomTypeName).containsExactly("Deluxe Twin");
    }

    @Test
    void T55_A_매핑이_없으면_A_를_호출하지_않고_NO_MAPPING_실패를_남긴다() {
        seed(SupplierCode.B, "B77120", "Riverside Hotel Seoul", "R-401", "Deluxe Twin Room");
        supplierBReturnsMockData();

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(supplierA.availabilityCalls).isEmpty();
        assertThat(result.failures()).containsExactly(
                new SearchFailure(SupplierCode.A, FailureReason.NO_MAPPING, "no active stays for supplier"));
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void T56_숙소_120개는_50_50_20_세_번으로_나눠_호출한다() {
        IntStream.range(0, 120).forEach(i -> seed(SupplierCode.A, "H-%03d".formatted(i), "Hotel " + i, "R", "Room"));

        service().search(SEP_1_TO_4);

        assertThat(supplierA.availabilityCalls).extracting(List::size).containsExactlyInAnyOrder(50, 50, 20);
        List<String> allCodes = supplierA.availabilityCalls.stream().flatMap(List::stream).sorted().toList();
        assertThat(allCodes).hasSize(120).doesNotHaveDuplicates();
    }

    @Test
    void T57_세_청크_중_하나만_실패하면_나머지_청크_결과는_반환하고_실패는_공급사당_한_건() {
        IntStream.range(0, 120).forEach(i -> seed(SupplierCode.A, "H-%03d".formatted(i), "Hotel " + i, "R", "Room"));
        supplierA.availabilityReturns((codes, criteria) -> codes.contains("H-050")
                ? failure(FailureReason.UNAVAILABLE, "503")
                : success(codes.stream().map(code -> offer(SupplierCode.A, code, "R", 1, 100)).toArray(Offer[]::new)));

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).hasSize(70);
        List<SearchFailure> failuresOfA = result.failures().stream()
                .filter(failure -> failure.supplier() == SupplierCode.A).toList();
        assertThat(failuresOfA).hasSize(1);
        assertThat(failuresOfA.get(0).reason()).isEqualTo(FailureReason.UNAVAILABLE);
        assertThat(failuresOfA.get(0).message()).startsWith("1/3 chunks failed");
    }

    @Test
    void T58_예산_안에_응답이_오지_않으면_예산_시점에_끊고_TIMEOUT_실패로_남긴다() {
        seedMockData();
        supplierA.availabilityReturns((codes, criteria) ->
                Mono.delay(Duration.ofSeconds(3)).then(success(offer(SupplierCode.A, "A-10023", "DLX-TWN", 1, 100))));
        supplierB.availabilityReturns((codes, criteria) ->
                Mono.delay(Duration.ofSeconds(3)).then(success(offer(SupplierCode.B, "B77120", "R-401", 1, 100))));
        long started = System.nanoTime();

        SearchResult result = service(Duration.ofSeconds(1), 50, 4).search(SEP_1_TO_4);

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(elapsedMillis).isLessThan(1_500L);
        assertThat(result.items()).isEmpty();
        assertThat(result.failures()).hasSize(2).allSatisfy(failure -> {
            assertThat(failure.reason()).isEqualTo(FailureReason.TIMEOUT);
            assertThat(failure.message()).contains("search budget exceeded");
        });
    }

    @Test
    void SR02_예산을_넘긴_공급사만_TIMEOUT_이고_먼저_온_공급사_결과는_반환한다() {
        seedMockData();
        supplierAReturnsMockData();
        supplierB.availabilityReturns((codes, criteria) -> Mono.never());

        SearchResult result = service(Duration.ofMillis(500), 50, 4).search(SEP_1_TO_4);

        assertThat(result.items()).extracting(SearchItem::supplier).containsExactly(SupplierCode.A);
        assertThat(result.failures()).containsExactly(new SearchFailure(SupplierCode.B, FailureReason.TIMEOUT,
                "1/1 chunks failed: search budget exceeded"));
    }

    @Test
    void SR02_공급사_안에서_동시에_나가는_청크_수를_설정값으로_제한한다() {
        IntStream.range(0, 12).forEach(i -> seed(SupplierCode.A, "H-%03d".formatted(i), "Hotel " + i, "R", "Room"));
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        supplierA.availabilityReturns((codes, criteria) -> Mono.defer(() -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            // 결과를 내보내기 직전에 줄인다. 완료 뒤에 줄이면 Reactor 가 다음 청크를 먼저 구독해 겹쳐 세어진다.
            return Mono.delay(Duration.ofMillis(100))
                    .map(tick -> {
                        inFlight.decrementAndGet();
                        return SupplierResult.<List<Offer>>success(List.of());
                    });
        }));

        SearchResult result = service(BUDGET, 2, 2).search(SEP_1_TO_4);

        assertThat(supplierA.availabilityCalls).hasSize(6);
        assertThat(maxInFlight.get()).isEqualTo(2);
        assertThat(result.failures()).extracting(SearchFailure::supplier).doesNotContain(SupplierCode.A);
    }

    @Test
    void T59_투숙_인원이_최대_수용_인원보다_많으면_제외한다() {
        seedMockData();
        supplierAReturnsMockData();
        var threeGuests = new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 1);

        SearchResult result = service().search(threeGuests);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void T60_숙소명과_객실_타입명은_매핑_DB_값을_쓴다() {
        seed(SupplierCode.A, "A-10023", "Renamed Hotel", "DLX-TWN", "Renamed Room");
        supplierAReturnsMockData();

        SearchResult result = service().search(SEP_1_TO_4);

        assertThat(result.items()).extracting(SearchItem::stayName, SearchItem::roomTypeName)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Renamed Hotel", "Renamed Room"));
    }

    @Test
    void SR02_결과는_공급사_stayId_roomTypeId_순으로_정렬한다() {
        Stay second = repository.upsertStay(SupplierCode.A, "A-2", "Second");
        Stay first = repository.upsertStay(SupplierCode.A, "A-1", "First");
        repository.upsertRoomType(second.id(), "R2", "Room 2", 2);
        repository.upsertRoomType(second.id(), "R1", "Room 1", 2);
        repository.upsertRoomType(first.id(), "R", "Room", 2);
        seed(SupplierCode.B, "B-1", "B Hotel", "R", "Room");
        supplierB.availabilityReturns((codes, criteria) -> success(offer(SupplierCode.B, "B-1", "R", 1, 100)));
        supplierA.availabilityReturns((codes, criteria) -> success(
                offer(SupplierCode.A, "A-1", "R", 1, 100),
                offer(SupplierCode.A, "A-2", "R1", 1, 100),
                offer(SupplierCode.A, "A-2", "R2", 1, 100)));

        SearchResult result = service().search(SEP_1_TO_4);

        List<String> order = new ArrayList<>();
        result.items().forEach(item -> order.add(item.supplier() + ":" + item.stayName() + ":" + item.roomTypeName()));
        assertThat(order).containsExactly(
                "A:Second:Room 2", "A:Second:Room 1", "A:First:Room", "B:B Hotel:Room");
    }
}
