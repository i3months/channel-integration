package com.stayhub.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupplierResultTest {

    @Test
    void DM03_success_는_값을_담고_isSuccess_가_참() {
        SupplierResult<String> result = SupplierResult.success("ok");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result).isInstanceOfSatisfying(SupplierResult.Success.class,
                success -> assertThat(success.value()).isEqualTo("ok"));
    }

    @Test
    void DM03_failure_는_사유와_메시지를_담고_isFailure_가_참() {
        SupplierResult<String> result = SupplierResult.failure(FailureReason.TIMEOUT, "response timeout after 3000ms");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class, failure -> {
            assertThat(failure.reason()).isEqualTo(FailureReason.TIMEOUT);
            assertThat(failure.message()).isEqualTo("response timeout after 3000ms");
        });
    }
}
