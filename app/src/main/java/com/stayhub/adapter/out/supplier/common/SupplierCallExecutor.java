package com.stayhub.adapter.out.supplier.common;

import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;

/**
 * 모든 공급사 호출이 통과하는 한 곳.
 * 응답 타임아웃, 예외의 실패 결과 변환, 재시도를 여기서만 건다. 반환 Mono 는 onError 로 끝나지 않는다.
 * 지표와 로그(OB-01)는 10단계에서 여기에 붙인다.
 */
public class SupplierCallExecutor {

    private final RetryRegistry retryRegistry;

    public SupplierCallExecutor(IntegrationProperties.Retry retryProperties) {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(retryProperties.maxAttempts())
                .waitDuration(retryProperties.waitDuration())
                .retryOnResult(SupplierCallExecutor::isRetryableFailure)
                // 예외는 아래에서 이미 Failure 로 바뀌므로 예외로는 재시도하지 않는다
                .retryOnException(error -> false)
                // 재시도 후에도 실패면 예외 대신 마지막 결과를 그대로 돌려준다
                .failAfterMaxAttempts(false)
                .build();
        this.retryRegistry = RetryRegistry.of(config);
    }

    public <T> Mono<SupplierResult<T>> execute(
            SupplierCode supplier, String api, Duration responseTimeout, Mono<SupplierResult<T>> call) {
        Mono<SupplierResult<T>> attempt = Mono.defer(() -> call)
                .timeout(responseTimeout)
                .onErrorResume(TimeoutException.class, error -> Mono.just(SupplierResult.failure(
                        FailureReason.TIMEOUT, "response timeout after " + responseTimeout.toMillis() + "ms")))
                .onErrorResume(error -> Mono.just(FailureClassifier.classify(error)));

        return attempt
                .transformDeferred(RetryOperator.of(retryFor(supplier)))
                .onErrorResume(error -> Mono.just(FailureClassifier.classify(error)));
    }

    Retry retryFor(SupplierCode supplier) {
        return retryRegistry.retry("supplier-" + supplier.name().toLowerCase(Locale.ROOT));
    }

    private static boolean isRetryableFailure(Object result) {
        return result instanceof SupplierResult.Failure<?> failure && failure.reason().retryable();
    }
}
