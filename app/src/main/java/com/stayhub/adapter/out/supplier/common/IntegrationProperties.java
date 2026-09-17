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

    /**
     * 설정 키는 integration.retry.wait 이다.
     * record 컴포넌트 이름을 wait 로 두면 Object.wait() 와 충돌하므로 @Name 으로 키만 맞춘다.
     */
    public record Retry(int maxAttempts, @Name("wait") Duration waitDuration) {
    }
}
