package com.stayhub.domain.port;

import com.stayhub.domain.SupplierCode;

/**
 * 공급사 하나의 연동 어댑터. 신규 공급사는 이 인터페이스 구현 하나와 설정 블록 하나로 추가된다.
 */
public interface SupplierAdapter extends SupplierCatalogPort, SupplierAvailabilityPort {

    SupplierCode supplierCode();
}
