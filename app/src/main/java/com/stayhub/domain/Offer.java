package com.stayhub.domain;

/**
 * 어댑터가 반환하는 판매 조건의 표준 형태. 공급사 코드만 있고 내부 식별자는 없다. 검색 서비스가 붙인다.
 * 저장하지 않는다.
 */
public record Offer(
        SupplierCode supplier,
        String supplierHotelCode,
        String supplierRoomTypeCode,
        int availableRooms,
        Price price) {
}
