package com.stayhub.adapter.out.supplier.a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierCallExecutor;
import com.stayhub.adapter.out.supplier.common.WebClientFactory;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import com.stayhub.domain.port.SupplierAdapter;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

/**
 * Supplier A 연동. 날짜별 1박 단가, 세금 별도, HTTP 상태 코드로 실패 표현.
 * HTTP 오류는 예외로 흘리지 않고 여기서 상태 코드와 본문을 보고 실패 결과로 만든다.
 * 타임아웃과 재시도는 SupplierCallExecutor 가 건다.
 */
@Component
public class SupplierAAdapter implements SupplierAdapter {

    private final WebClientFactory webClients;
    private final SupplierCallExecutor executor;
    private final IntegrationProperties integration;
    private final SupplierAMapper mapper;
    private final SupplierAFailureMapper failureMapper;
    private final ObjectMapper objectMapper;

    public SupplierAAdapter(
            WebClientFactory webClients,
            SupplierCallExecutor executor,
            IntegrationProperties integration,
            SupplierAMapper mapper,
            SupplierAFailureMapper failureMapper,
            ObjectMapper objectMapper) {
        this.webClients = webClients;
        this.executor = executor;
        this.integration = integration;
        this.mapper = mapper;
        this.failureMapper = failureMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public SupplierCode supplierCode() {
        return SupplierCode.A;
    }

    @Override
    public Mono<SupplierResult<List<CatalogEntry>>> fetchCatalog() {
        Mono<SupplierResult<List<CatalogEntry>>> call =
                get(uri -> uri.path("/a/v1/hotels").build(), mapper::toCatalog);
        return executor.execute(SupplierCode.A, "catalog", integration.catalogResponseTimeout(), call);
    }

    @Override
    public Mono<SupplierResult<List<Offer>>> fetchAvailability(List<String> supplierHotelCodes, StaySearchCriteria criteria) {
        Mono<SupplierResult<List<Offer>>> call = get(uri -> uri.path("/a/v1/availability")
                        .queryParam("hotelCodes", String.join(",", supplierHotelCodes))
                        .queryParam("checkIn", criteria.checkIn())
                        .queryParam("checkOut", criteria.checkOut())
                        .queryParam("adults", criteria.adults())
                        .queryParam("children", criteria.children())
                        .build(),
                items -> mapper.toOffers(items, criteria));
        return executor.execute(SupplierCode.A, "availability", integration.availabilityResponseTimeout(), call);
    }

    private <T> Mono<SupplierResult<T>> get(Function<UriBuilder, URI> uri, Function<JsonNode, T> itemsMapper) {
        return webClients.webClient(SupplierCode.A).get()
                .uri(uri)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> response.statusCode().is2xxSuccessful()
                                ? parse(body, itemsMapper)
                                : failureMapper.<T>toFailure(response.statusCode().value(), body)));
    }

    private <T> SupplierResult<T> parse(String body, Function<JsonNode, T> itemsMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return SupplierResult.failure(FailureReason.MALFORMED, e.getClass().getSimpleName() + ": body is not JSON");
        }
        if (root == null || !root.path("items").isArray()) {
            return SupplierResult.failure(FailureReason.MALFORMED, "items array missing");
        }
        return SupplierResult.success(itemsMapper.apply(root.get("items")));
    }
}
