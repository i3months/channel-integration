package com.stayhub.application;

import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;

/**
 * 공급사 하나의 목록 동기화 결과. 성공이면 failureReason 과 failureMessage 는 null 이다.
 * deactivated 는 비활성화된 숙소 수다. 객실 타입 비활성화 수는 세지 않는다.
 */
public record SyncResult(
        SupplierCode supplier,
        int added,
        int updated,
        int deactivated,
        FailureReason failureReason,
        String failureMessage) {

    public static SyncResult failure(SupplierCode supplier, FailureReason reason, String message) {
        return new SyncResult(supplier, 0, 0, 0, reason, message);
    }

    public boolean isSuccess() {
        return failureReason == null;
    }
}
