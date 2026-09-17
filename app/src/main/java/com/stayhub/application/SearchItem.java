package com.stayhub.application;

import com.stayhub.domain.Price;
import com.stayhub.domain.SupplierCode;

/**
 * 검색 결과 한 건. 식별자와 이름, 최대 인원은 매핑 DB 값이고 재고와 요금은 공급사 응답 값이다.
 */
public record SearchItem(
        long stayId,
        String stayName,
        long roomTypeId,
        String roomTypeName,
        int maxOccupancy,
        int availableRooms,
        SupplierCode supplier,
        Price price) {
}
