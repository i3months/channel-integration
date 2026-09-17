package com.stayhub.application;

import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.port.SupplierAdapter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 스프링이 주입한 어댑터 목록을 공급사 코드로 찾는다. 신규 공급사는 어댑터 빈만 추가하면 여기에 잡힌다.
 */
public class SupplierRegistry {

    private final Map<SupplierCode, SupplierAdapter> adapters = new EnumMap<>(SupplierCode.class);

    public SupplierRegistry(List<SupplierAdapter> adapters) {
        for (SupplierAdapter adapter : adapters) {
            SupplierAdapter previous = this.adapters.putIfAbsent(adapter.supplierCode(), adapter);
            if (previous != null) {
                throw new IllegalStateException("duplicate adapter for supplier: " + adapter.supplierCode());
            }
        }
    }

    public Optional<SupplierAdapter> find(SupplierCode supplier) {
        return Optional.ofNullable(adapters.get(supplier));
    }

    /** EnumMap 이므로 값 순서가 곧 SupplierCode 선언 순서다. */
    public List<SupplierAdapter> all() {
        return List.copyOf(adapters.values());
    }
}
