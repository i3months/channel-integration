package com.stayhub.adapter.out.supplier.a;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;

/**
 * Supplier A 응답 형태. 이 패키지 밖으로 나가지 않는다.
 * 필수 여부를 판정하려고 숫자와 boolean 은 박싱 타입으로 받는다.
 * 목록의 roomTypes 는 객실 타입 하나가 깨져도 나머지를 살리려고 JsonNode 로 받아 하나씩 변환한다.
 */
final class SupplierADtos {

    private SupplierADtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Hotel(String hotelCode, String hotelName, List<JsonNode> roomTypes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HotelRoomType(String roomTypeCode, String roomTypeName, Integer maxOccupancy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AvailabilityItem(
            String hotelCode,
            String hotelName,
            String roomTypeCode,
            String roomTypeName,
            Integer maxOccupancy,
            Boolean breakfastIncluded,
            String currency,
            List<DailyRate> dailyRates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DailyRate(LocalDate date, Integer remainingRooms, Long nightlyRate, Long taxAmount) {
    }
}
