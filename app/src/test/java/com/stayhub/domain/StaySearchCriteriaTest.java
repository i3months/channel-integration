package com.stayhub.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StaySearchCriteriaTest {

    @Test
    void T14_09_01_체크인_09_04_체크아웃이면_3박이고_숙박일은_01_02_03() {
        var criteria = new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

        assertThat(criteria.nights()).isEqualTo(3);
        assertThat(criteria.stayDates()).containsExactly(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3));
    }

    @Test
    void DM04_투숙_인원은_성인과_아동의_합() {
        var criteria = new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 2, 1);

        assertThat(criteria.guests()).isEqualTo(3);
    }
}
