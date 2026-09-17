package com.stayhub.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 연박 검색에서 예약 가능 객실 수를 정한다.
 * 한 객실로 이어서 묵으려면 숙박일마다 그 타입이 남아 있어야 하므로 날짜별 잔여 수의 최솟값이다.
 */
public final class AvailabilityRule {

    private AvailabilityRule() {
    }

    public static int availableRooms(List<LocalDate> stayDates, Map<LocalDate, Integer> remainingByDate) {
        int min = Integer.MAX_VALUE;
        for (LocalDate date : stayDates) {
            Integer remaining = remainingByDate.get(date);
            if (remaining == null || remaining <= 0) {
                return 0;
            }
            min = Math.min(min, remaining);
        }
        return stayDates.isEmpty() ? 0 : min;
    }
}
