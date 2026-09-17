package com.stayhub.domain;

/**
 * 매핑 저장소에 있는 숙소. id 가 내부 숙소 식별자다.
 */
public record Stay(long id, SupplierCode supplier, String supplierHotelCode, String name, boolean active) {
}
