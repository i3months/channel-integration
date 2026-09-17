package com.stayhub.adapter.out.supplier.b;

import com.fasterxml.jackson.databind.JsonNode;
import com.stayhub.adapter.out.supplier.common.FailureClassifier;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierResult;
import org.springframework.stereotype.Component;

/**
 * Supplier B 는 장애 상황에서도 HTTP 200 을 주고 본문 resultCode 로만 실패를 알린다.
 * 이 코드를 A 의 HTTP 상태 코드와 같은 FailureReason 으로 맞춘다.
 */
@Component
public class SupplierBFailureMapper {

    static final String SUCCESS = "0000";
    private static final int BODY_SNIPPET_LENGTH = 200;

    /** resultCode 가 0000 이 아닐 때, 또는 resultCode 가 없을 때 호출한다. */
    public <T> SupplierResult.Failure<T> fromResultCode(JsonNode root) {
        JsonNode resultCode = root.get("resultCode");
        if (resultCode == null || resultCode.isNull()) {
            return new SupplierResult.Failure<>(FailureReason.MALFORMED, "resultCode missing");
        }
        String code = resultCode.asText();
        FailureReason reason = switch (code) {
            case "E400" -> FailureReason.BAD_REQUEST;
            case "E401" -> FailureReason.UNAUTHORIZED;
            case "E429" -> FailureReason.RATE_LIMITED;
            case "E503" -> FailureReason.UNAVAILABLE;
            default -> FailureReason.SUPPLIER_ERROR;
        };
        return new SupplierResult.Failure<>(reason, code + " " + root.path("resultMessage").asText(""));
    }

    /** 스펙상 B 는 항상 200 이지만, 게이트웨이 오류 등에 대비해 공통 상태 코드 규칙을 쓴다. */
    public <T> SupplierResult.Failure<T> fromHttpStatus(int status, String body) {
        String message = body == null || body.isBlank()
                ? String.valueOf(status)
                : status + " body=" + body.substring(0, Math.min(BODY_SNIPPET_LENGTH, body.length()));
        return new SupplierResult.Failure<>(FailureClassifier.reasonForStatus(status), message);
    }
}
