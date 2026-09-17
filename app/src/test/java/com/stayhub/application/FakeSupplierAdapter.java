package com.stayhub.application;

import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import com.stayhub.domain.port.SupplierAdapter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * 서비스 테스트용 어댑터. 응답을 테스트가 정하고, 호출 횟수와 받은 코드 묶음을 기록한다.
 */
public class FakeSupplierAdapter implements SupplierAdapter {

    private final SupplierCode supplierCode;
    private volatile Supplier<Mono<SupplierResult<List<CatalogEntry>>>> catalog =
            () -> Mono.just(SupplierResult.success(List.of()));
    private volatile BiFunction<List<String>, StaySearchCriteria, Mono<SupplierResult<List<Offer>>>> availability =
            (codes, criteria) -> Mono.just(SupplierResult.success(List.of()));

    public final AtomicInteger catalogCalls = new AtomicInteger();
    public final List<List<String>> availabilityCalls = new CopyOnWriteArrayList<>();

    public FakeSupplierAdapter(SupplierCode supplierCode) {
        this.supplierCode = supplierCode;
    }

    public FakeSupplierAdapter catalogReturns(List<CatalogEntry> entries) {
        this.catalog = () -> Mono.just(SupplierResult.success(entries));
        return this;
    }

    public FakeSupplierAdapter catalogReturns(Supplier<Mono<SupplierResult<List<CatalogEntry>>>> catalog) {
        this.catalog = catalog;
        return this;
    }

    public FakeSupplierAdapter availabilityReturns(
            BiFunction<List<String>, StaySearchCriteria, Mono<SupplierResult<List<Offer>>>> availability) {
        this.availability = availability;
        return this;
    }

    @Override
    public SupplierCode supplierCode() {
        return supplierCode;
    }

    @Override
    public Mono<SupplierResult<List<CatalogEntry>>> fetchCatalog() {
        return Mono.defer(() -> {
            catalogCalls.incrementAndGet();
            return catalog.get();
        });
    }

    @Override
    public Mono<SupplierResult<List<Offer>>> fetchAvailability(List<String> codes, StaySearchCriteria criteria) {
        return Mono.defer(() -> {
            availabilityCalls.add(List.copyOf(codes));
            return availability.apply(codes, criteria);
        });
    }
}
