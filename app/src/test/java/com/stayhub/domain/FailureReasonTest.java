package com.stayhub.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class FailureReasonTest {

    @Test
    void T12_재시도_가능한_사유는_TIMEOUT_UNAVAILABLE_RATE_LIMITED_SUPPLIER_ERROR_뿐이다() {
        var retryable = EnumSet.noneOf(FailureReason.class);
        Arrays.stream(FailureReason.values()).filter(FailureReason::retryable).forEach(retryable::add);

        assertThat(retryable).containsExactlyInAnyOrder(
                FailureReason.TIMEOUT,
                FailureReason.UNAVAILABLE,
                FailureReason.RATE_LIMITED,
                FailureReason.SUPPLIER_ERROR);
    }

    @Test
    void DM02_사유는_여덟_가지다() {
        assertThat(FailureReason.values()).containsExactly(
                FailureReason.TIMEOUT,
                FailureReason.UNAVAILABLE,
                FailureReason.RATE_LIMITED,
                FailureReason.SUPPLIER_ERROR,
                FailureReason.BAD_REQUEST,
                FailureReason.UNAUTHORIZED,
                FailureReason.MALFORMED,
                FailureReason.NO_MAPPING);
    }
}
