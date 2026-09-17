package com.stayhub.adapter.out.supplier.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * FX-03. 동기화 대기 시간이 목록 호출의 재시도까지 담아야 한다.
 */
class IntegrationPropertiesTest {

    private static IntegrationProperties properties(Duration catalogTimeout, int maxAttempts, Duration wait) {
        return new IntegrationProperties(true, Duration.ofSeconds(1), Duration.ofSeconds(3), catalogTimeout,
                Duration.ofSeconds(5), 50, 4, new IntegrationProperties.Retry(maxAttempts, wait));
    }

    @Test
    void T106_기본_설정이면_두_번의_목록_타임아웃과_재시도_대기와_여유_1초를_더해_21_3초() {
        var defaults = properties(Duration.ofSeconds(10), 2, Duration.ofMillis(300));

        assertThat(defaults.catalogSyncWait()).isEqualTo(Duration.ofMillis(21_300));
    }

    @Test
    void T107_시도_1번이면_대기_없이_타임아웃과_여유만_더한다() {
        var singleAttempt = properties(Duration.ofSeconds(2), 1, Duration.ofMillis(500));

        assertThat(singleAttempt.catalogSyncWait()).isEqualTo(Duration.ofSeconds(3));
    }
}
