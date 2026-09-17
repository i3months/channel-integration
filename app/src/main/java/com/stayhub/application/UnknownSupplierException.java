package com.stayhub.application;

import com.stayhub.domain.SupplierCode;

/**
 * 어댑터가 등록되지 않은 공급사. 동기화 API 는 이 예외만 404 로 바꾸고,
 * 반영 중에 난 다른 IllegalArgumentException 은 500 으로 둔다 (FX-04).
 */
public class UnknownSupplierException extends IllegalArgumentException {

    public UnknownSupplierException(SupplierCode supplier) {
        super("no adapter for supplier: " + supplier);
    }
}
