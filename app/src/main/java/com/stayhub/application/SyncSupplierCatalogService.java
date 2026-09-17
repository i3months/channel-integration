package com.stayhub.application;

import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import com.stayhub.domain.port.SupplierAdapter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import reactor.core.publisher.Mono;

/**
 * 공급사 숙소 목록을 받아 매핑에 반영한다.
 * 목록 호출이 실패하면 DB 를 건드리지 않아 기존 매핑이 그대로 남는다.
 *
 * 같은 공급사의 동기화는 한 번에 하나만 돈다. 기동 시 동기화와 수동 동기화가 겹치면
 * 둘 다 같은 숙소를 새로 추가하려다 유일 제약 위반이 나기 때문이다.
 * 잠금은 JVM 안에서만 유효하다. 여러 인스턴스로 띄우면 DB 수준 잠금이 필요하다.
 */
public class SyncSupplierCatalogService {

    private final SupplierRegistry registry;
    private final CatalogMappingWriter writer;
    private final Duration catalogWait;
    private final Map<SupplierCode, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * @param catalogWait 목록 응답을 기다리는 최대 시간. 목록 응답 타임아웃보다 조금 길게 준다.
     */
    public SyncSupplierCatalogService(SupplierRegistry registry, CatalogMappingWriter writer, Duration catalogWait) {
        this.registry = registry;
        this.writer = writer;
        this.catalogWait = catalogWait;
    }

    public SyncResult sync(SupplierCode supplier) {
        ReentrantLock lock = locks.computeIfAbsent(supplier, code -> new ReentrantLock());
        lock.lock();
        try {
            return syncLocked(supplier);
        } finally {
            lock.unlock();
        }
    }

    private SyncResult syncLocked(SupplierCode supplier) {
        SupplierAdapter adapter = registry.find(supplier)
                .orElseThrow(() -> new UnknownSupplierException(supplier));

        SupplierResult<List<CatalogEntry>> result = adapter.fetchCatalog()
                .timeout(catalogWait, Mono.just(SupplierResult.failure(
                        FailureReason.TIMEOUT, "catalog not received within " + catalogWait.toMillis() + "ms")))
                .block();

        return switch (result) {
            case SupplierResult.Failure<List<CatalogEntry>> failure ->
                    SyncResult.failure(supplier, failure.reason(), failure.message());
            case SupplierResult.Success<List<CatalogEntry>> success -> writer.apply(supplier, success.value());
        };
    }
}
