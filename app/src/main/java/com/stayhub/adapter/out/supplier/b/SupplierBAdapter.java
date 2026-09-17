package com.stayhub.adapter.out.supplier.b;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

/**
 * Supplier B 연동. 기간 전체 총액, 세금 포함, 항상 HTTP 200 + 본문 resultCode 로 실패 표현.
 * resultCode 를 확인하지 않으면 장애를 정상 응답으로 처리하게 되므로, 본문 판정이 항목 변환보다 먼저다.
 */
@Component
public class SupplierBAdapter implements SupplierAdapter {

    private final WebClientFactory webClients;
    private final SupplierCallExecutor executor;
    private final IntegrationProperties integration;
    private final SupplierBMapper mapper;
    private final SupplierBFailureMapper failureMapper;
    private final ObjectMapper objectMapper;

    public SupplierBAdapter(
            WebClientFactory webClients,
            SupplierCallExecutor executor,
            IntegrationProperties integration,
            SupplierBMapper mapper,
            SupplierBFailureMapper failureMapper,
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
        return SupplierCode.B;
    }

    @Override
    public Mono<SupplierResult<List<CatalogEntry>>> fetchCatalog() {
        Mono<SupplierResult<List<CatalogEntry>>> call =
                get(uri -> uri.path("/b/api/properties").build(), mapper::toCatalog);
        return executor.execute(SupplierCode.B, "catalog", integration.catalogResponseTimeout(), call);
    }

    @Override
    public Mono<SupplierResult<List<Offer>>> fetchAvailability(List<String> supplierHotelCodes, StaySearchCriteria criteria) {
        Map<String, Object> variables = new HashMap<>();
        String codesTemplate = codeVariables(supplierHotelCodes, variables);
        variables.put("checkIn", criteria.checkIn());
        variables.put("checkOut", criteria.checkOut());
        variables.put("adults", criteria.adults());
        variables.put("children", criteria.children());
        Mono<SupplierResult<List<Offer>>> call = get(uri -> uri.path("/b/api/search")
                        .queryParam("propertyIds", codesTemplate)
                        .queryParam("checkIn", "{checkIn}")
                        .queryParam("checkOut", "{checkOut}")
                        .queryParam("adults", "{adults}")
                        .queryParam("children", "{children}")
                        .build(variables),
                items -> mapper.toOffers(items, criteria));
        return executor.execute(SupplierCode.B, "availability", integration.availabilityResponseTimeout(), call);
    }

    /**
     * 숙소 코드를 코드마다 URI 변수 하나로 넘긴다 (FX-02).
     * 값은 변수 단위로 인코딩되어 `+`, `{`, `&` 같은 문자가 그대로 전달되고, 템플릿의 구분자 쉼표는 그대로 나간다.
     */
    private static String codeVariables(List<String> codes, Map<String, Object> variables) {
        StringBuilder template = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                template.append(',');
            }
            template.append("{code").append(i).append('}');
            variables.put("code" + i, codes.get(i));
        }
        return template.toString();
    }

    /** URI 조립 중 예외도 호출 실행기가 실패 결과로 바꾸도록 구독 시점까지 미룬다 (FX-02). */
    private <T> Mono<SupplierResult<T>> get(Function<UriBuilder, URI> uri, Function<JsonNode, T> itemsMapper) {
        return Mono.defer(() -> request(uri, itemsMapper));
    }

    private <T> Mono<SupplierResult<T>> request(Function<UriBuilder, URI> uri, Function<JsonNode, T> itemsMapper) {
        return webClients.webClient(SupplierCode.B).get()
                .uri(uri)
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> response.statusCode().is2xxSuccessful()
                                ? parse(body, itemsMapper)
                                : failureMapper.<T>fromHttpStatus(response.statusCode().value(), body)));
    }

    private <T> SupplierResult<T> parse(String body, Function<JsonNode, T> itemsMapper) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            return SupplierResult.failure(FailureReason.MALFORMED, e.getClass().getSimpleName() + ": body is not JSON");
        }
        if (root == null || !root.isObject()) {
            return SupplierResult.failure(FailureReason.MALFORMED, "body is not a JSON object");
        }
        if (!SupplierBFailureMapper.SUCCESS.equals(root.path("resultCode").asText(null))) {
            return failureMapper.fromResultCode(root);
        }
        JsonNode items = root.path("data").path("items");
        if (!items.isArray()) {
            return SupplierResult.failure(FailureReason.MALFORMED, "data.items array missing");
        }
        return SupplierResult.success(itemsMapper.apply(items));
    }
}
