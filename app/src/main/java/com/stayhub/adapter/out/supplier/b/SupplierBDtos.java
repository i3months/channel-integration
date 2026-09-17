package com.stayhub.adapter.out.supplier.b;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;

/**
 * Supplier B 응답 항목 형태. 이 패키지 밖으로 나가지 않는다.
 * 봉투(resultCode, resultMessage, data)는 어댑터가 JsonNode 로 먼저 판정하고, items 의 항목만 이 DTO 로 읽는다.
 */
final class SupplierBDtos {

    private SupplierBDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Property(String propertyId, String propertyName, List<JsonNode> rooms) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PropertyRoom(String roomId, String roomName, Integer maxOccupancy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchItem(
            String propertyId,
            String propertyName,
            String roomId,
            String roomName,
            Integer maxOccupancy,
            Boolean breakfastIncluded,
            String currency,
            Long totalPrice,
            List<Inventory> inventory) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Inventory(LocalDate date, Integer remainingRooms) {
    }
}
