package com.stayhub.adapter.out.supplier.a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.adapter.out.supplier.common.FailureClassifier;
import com.stayhub.domain.SupplierResult;
import org.springframework.stereotype.Component;

/**
 * Supplier A 는 HTTP 상태 코드로 실패를 알린다. 사유 판정은 공통 규칙(AD-03)을 그대로 쓴다.
 */
@Component
public class SupplierAFailureMapper {

    private static final int BODY_SNIPPET_LENGTH = 200;

    private final ObjectMapper objectMapper;

    public SupplierAFailureMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> SupplierResult.Failure<T> toFailure(int status, String body) {
        return new SupplierResult.Failure<>(FailureClassifier.reasonForStatus(status), message(status, body));
    }

    private String message(int status, String body) {
        if (body == null || body.isBlank()) {
            return String.valueOf(status);
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("error")) {
                return status + " " + node.get("error").asText() + ": " + node.path("message").asText("");
            }
        } catch (Exception ignored) {
            // JSON 이 아닌 오류 본문이면 본문 앞부분을 남긴다
        }
        return status + " body=" + body.substring(0, Math.min(BODY_SNIPPET_LENGTH, body.length()));
    }
}
