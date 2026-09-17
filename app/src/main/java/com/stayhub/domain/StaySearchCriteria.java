package com.stayhub.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 검색 조건. 검증은 API 계층이 한다. 체크아웃일은 숙박일에 포함되지 않는다.
 */
public record StaySearchCriteria(LocalDate checkIn, LocalDate checkOut, int adults, int children) {

    public int nights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public List<LocalDate> stayDates() {
        return checkIn.datesUntil(checkOut).toList();
    }

    public int guests() {
        return adults + children;
    }
}
