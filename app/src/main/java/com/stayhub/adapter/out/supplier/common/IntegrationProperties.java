package com.stayhub.adapter.out.supplier.common;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties("integration")
public record IntegrationProperties(
        boolean syncOnStartup,
        Duration connectTimeout,
        Duration availabilityResponseTimeout,
        Duration catalogResponseTimeout,
        Duration searchBudget,
        int chunkSize,
        int chunkConcurrency,
        Retry retry) {

    private static final Duration SYNC_MARGIN = Duration.ofSeconds(1);

    /**
     * 동기화가 목록 응답을 기다리는 최대 시간 (FX-03).
     * 동기화는 사용자가 기다리는 요청이 아니므로 재시도까지 끝나도록 모든 시도와 대기를 더하고 여유를 둔다.
     * 검색은 반대로 전체 시간 예산이 우선이다.
     */
    public Duration catalogSyncWait() {
        int attempts = Math.max(1, retry.maxAttempts());
        return catalogResponseTimeout.multipliedBy(attempts)
                .plus(retry.waitDuration().multipliedBy(attempts - 1L))
                .plus(SYNC_MARGIN);
    }

    /**
     * 설정 키는 integration.retry.wait 이다.
     * record 컴포넌트 이름을 wait 로 두면 Object.wait() 와 충돌하므로 @Name 으로 키만 맞춘다.
     */
    public record Retry(int maxAttempts, @Name("wait") Duration waitDuration) {
    }
}
