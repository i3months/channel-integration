package com.stayhub.domain;

import java.time.LocalDate;

/**
 * 하루치 요금. nightlyRate 는 세금 별도 금액, taxAmount 는 그날 세액.
 */
public record DailyRate(LocalDate date, long nightlyRate, long taxAmount) {
}
