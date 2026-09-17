package com.stayhub.domain;

/**
 * 공급사 연동 실패 사유. 공급사마다 다른 실패 표현(A 는 HTTP 상태 코드, B 는 본문 resultCode)을 이 하나로 통일한다.
 */
public enum FailureReason {
    TIMEOUT(true),
    UNAVAILABLE(true),
    RATE_LIMITED(true),
    SUPPLIER_ERROR(true),
    BAD_REQUEST(false),
    UNAUTHORIZED(false),
    MALFORMED(false),
    NO_MAPPING(false);

    private final boolean retryable;

    FailureReason(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
