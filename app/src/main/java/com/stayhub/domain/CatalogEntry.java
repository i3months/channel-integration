package com.stayhub.domain;

import java.util.List;

/**
 * 공급사 숙소 목록 API 의 숙소 하나를 표준 형태로 옮긴 것.
 */
public record CatalogEntry(String supplierHotelCode, String name, List<CatalogRoomType> roomTypes) {

    public CatalogEntry {
        roomTypes = roomTypes == null ? List.of() : List.copyOf(roomTypes);
    }
}
