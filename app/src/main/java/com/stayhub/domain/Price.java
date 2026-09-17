package com.stayhub.domain;

import java.util.List;

/**
 * 요금 표준. totalAmount 는 요청 기간 전체의 세금 포함 총액이며 통화 최소 단위 정수다.
 * daily 는 날짜별 내역을 주는 공급사(A)만 채우고, 그렇지 않은 공급사(B)는 null 이다.
 */
public record Price(long totalAmount, String currency, boolean breakfastIncluded, int nights, List<DailyRate> daily) {

    public Price {
        daily = daily == null ? null : List.copyOf(daily);
    }
}
