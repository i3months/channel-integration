package com.stayhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stayhub.domain.SupplierCode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 기동 시 동기화가 도는 중에 수동 동기화가 들어오면, 둘 다 "없는 숙소"로 보고 같은 숙소를 추가하려다
 * 유일 제약 위반(500)이 난다. 실제 실행에서 발견한 문제라 테스트로 고정한다.
 */
class SyncConcurrencyTest {

    @Test
    void SY_같은_공급사의_동기화는_동시에_반영되지_않는다() throws Exception {
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        CatalogMappingWriter writer = mock(CatalogMappingWriter.class);
        when(writer.apply(any(), anyList())).thenAnswer(invocation -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            Thread.sleep(100);
            inFlight.decrementAndGet();
            return new SyncResult(SupplierCode.A, 0, 0, 0, null, null);
        });
        var supplierA = new FakeSupplierAdapter(SupplierCode.A).catalogReturns(List.of());
        var service = new SyncSupplierCatalogService(new SupplierRegistry(List.of(supplierA)), writer, Duration.ofSeconds(1));

        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                start.await();
                return service.sync(SupplierCode.A);
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(maxInFlight).hasValue(1);
    }

    @Test
    void SY_다른_공급사의_동기화는_서로_막지_않는다() throws Exception {
        var inFlight = new AtomicInteger();
        var maxInFlight = new AtomicInteger();
        CatalogMappingWriter writer = mock(CatalogMappingWriter.class);
        when(writer.apply(any(), anyList())).thenAnswer(invocation -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            Thread.sleep(200);
            inFlight.decrementAndGet();
            return new SyncResult(invocation.getArgument(0), 0, 0, 0, null, null);
        });
        var registry = new SupplierRegistry(List.of(
                new FakeSupplierAdapter(SupplierCode.A).catalogReturns(List.of()),
                new FakeSupplierAdapter(SupplierCode.B).catalogReturns(List.of())));
        var service = new SyncSupplierCatalogService(registry, writer, Duration.ofSeconds(1));

        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        for (SupplierCode code : SupplierCode.values()) {
            pool.submit(() -> {
                start.await();
                return service.sync(code);
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(maxInFlight).hasValue(2);
    }
}
