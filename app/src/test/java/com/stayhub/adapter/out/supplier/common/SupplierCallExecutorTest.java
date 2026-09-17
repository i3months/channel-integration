package com.stayhub.adapter.out.supplier.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SupplierCallExecutorTest {

    private static final Duration WAIT = Duration.ofMillis(50);
    private static final Duration TIMEOUT = Duration.ofMillis(100);

    private final SupplierCallExecutor executor =
            new SupplierCallExecutor(new IntegrationProperties.Retry(2, WAIT));

    /** 구독될 때마다 횟수를 세고, 회차별로 다른 결과를 내는 호출. */
    private static <T> Mono<SupplierResult<T>> counting(AtomicInteger attempts, Supplier<Mono<SupplierResult<T>>> perAttempt) {
        return Mono.defer(() -> {
            attempts.incrementAndGet();
            return perAttempt.get();
        });
    }

    private Mono<SupplierResult<String>> run(Mono<SupplierResult<String>> call) {
        return executor.execute(SupplierCode.A, "availability", TIMEOUT, call);
    }

    @Test
    void AD02_성공은_그대로_통과하고_한_번만_호출한다() {
        var attempts = new AtomicInteger();

        StepVerifier.create(run(counting(attempts, () -> Mono.just(SupplierResult.success("ok")))))
                .expectNext(SupplierResult.success("ok"))
                .verifyComplete();

        assertThat(attempts).hasValue(1);
    }

    @Test
    void AD02_응답_타임아웃을_넘기면_TIMEOUT_실패로_바꾸고_재시도_후에도_실패면_마지막_실패를_반환한다() {
        var attempts = new AtomicInteger();

        StepVerifier.create(run(counting(attempts, Mono::never)))
                .expectNext(SupplierResult.failure(FailureReason.TIMEOUT, "response timeout after 100ms"))
                .verifyComplete();

        assertThat(attempts).hasValue(2);
    }

    @Test
    void AD02_예외는_onError_로_끝나지_않고_실패_결과로_바뀐다() {
        var attempts = new AtomicInteger();

        StepVerifier.create(run(counting(attempts, () -> Mono.error(new IllegalStateException("boom")))))
                .assertNext(result -> assertThat(result)
                        .isEqualTo(SupplierResult.failure(FailureReason.SUPPLIER_ERROR, "IllegalStateException: boom")))
                .verifyComplete();
    }

    @Test
    void AD04_재시도_가능한_실패는_한_번_더_호출한다() {
        var attempts = new AtomicInteger();

        StepVerifier.create(run(counting(attempts,
                        () -> Mono.just(SupplierResult.failure(FailureReason.UNAVAILABLE, "503")))))
                .expectNext(SupplierResult.failure(FailureReason.UNAVAILABLE, "503"))
                .verifyComplete();

        assertThat(attempts).hasValue(2);
    }

    @Test
    void AD04_재시도_불가능한_실패는_다시_호출하지_않는다() {
        var attempts = new AtomicInteger();

        StepVerifier.create(run(counting(attempts,
                        () -> Mono.just(SupplierResult.failure(FailureReason.BAD_REQUEST, "400")))))
                .expectNext(SupplierResult.failure(FailureReason.BAD_REQUEST, "400"))
                .verifyComplete();

        assertThat(attempts).hasValue(1);
    }

    @Test
    void AD04_첫_호출이_재시도_가능한_실패이고_두_번째가_성공이면_성공을_반환한다() {
        var attempts = new AtomicInteger();

        Mono<SupplierResult<String>> call = counting(attempts, () -> attempts.get() == 1
                ? Mono.just(SupplierResult.failure(FailureReason.RATE_LIMITED, "429"))
                : Mono.just(SupplierResult.success("ok")));

        StepVerifier.create(run(call))
                .expectNext(SupplierResult.success("ok"))
                .verifyComplete();

        assertThat(attempts).hasValue(2);
    }

    @Test
    void AD04_재시도_전에_설정한_대기_시간만큼_기다린다() {
        var attempts = new AtomicInteger();
        long started = System.nanoTime();

        run(counting(attempts, () -> Mono.just(SupplierResult.<String>failure(FailureReason.UNAVAILABLE, "503"))))
                .block(Duration.ofSeconds(5));

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(WAIT.toMillis());
    }

    @Test
    void AD04_Retry_인스턴스는_공급사별로_supplier_소문자_코드_이름이다() {
        assertThat(executor.retryFor(SupplierCode.A).getName()).isEqualTo("supplier-a");
        assertThat(executor.retryFor(SupplierCode.B).getName()).isEqualTo("supplier-b");
        assertThat(executor.retryFor(SupplierCode.A)).isSameAs(executor.retryFor(SupplierCode.A));
    }
}
