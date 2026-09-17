package com.stayhub.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AvailabilityRuleTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate D3 = LocalDate.of(2026, 9, 3);
    private static final List<LocalDate> THREE_NIGHTS = List.of(D1, D2, D3);

    @Test
    void T01_잔여_3_1_5_이면_예약_가능_객실_수는_최솟값_1() {
        int available = AvailabilityRule.availableRooms(THREE_NIGHTS, Map.of(D1, 3, D2, 1, D3, 5));

        assertThat(available).isEqualTo(1);
    }

    @Test
    void T02_중간_날짜_잔여가_0_이면_0() {
        int available = AvailabilityRule.availableRooms(THREE_NIGHTS, Map.of(D1, 2, D2, 0, D3, 4));

        assertThat(available).isZero();
    }

    @Test
    void T03_숙박일_중_하루라도_데이터가_없으면_0() {
        int available = AvailabilityRule.availableRooms(THREE_NIGHTS, Map.of(D1, 3, D3, 5));

        assertThat(available).isZero();
    }

    @Test
    void T04_잔여에_음수가_있으면_0() {
        int available = AvailabilityRule.availableRooms(THREE_NIGHTS, Map.of(D1, 3, D2, -1, D3, 5));

        assertThat(available).isZero();
    }

    @Test
    void DM10_요청_숙박일_밖의_날짜는_판정에_쓰지_않는다() {
        LocalDate outside = LocalDate.of(2026, 9, 4);

        int available = AvailabilityRule.availableRooms(THREE_NIGHTS, Map.of(D1, 3, D2, 2, D3, 5, outside, 0));

        assertThat(available).isEqualTo(2);
    }
}
