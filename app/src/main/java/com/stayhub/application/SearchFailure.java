package com.stayhub.application;

import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;

/**
 * 검색 중 실패한 공급사. 공급사당 최대 한 건이다.
 */
public record SearchFailure(SupplierCode supplier, FailureReason reason, String message) {
}
