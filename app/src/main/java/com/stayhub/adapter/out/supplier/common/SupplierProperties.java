package com.stayhub.adapter.out.supplier.common;

import com.stayhub.domain.SupplierCode;
import java.util.Map;

/**
 * suppliers.{code} 아래의 공급사별 접속 설정.
 * 루트 키 자체가 Map 이라 @ConfigurationProperties record 로는 바인딩되지 않는다.
 * config 패키지에서 Binder 로 Map 을 읽어 이 record 를 만든다.
 */
public record SupplierProperties(Map<SupplierCode, Entry> entries) {

    public SupplierProperties {
        entries = Map.copyOf(entries);
    }

    public Entry entry(SupplierCode code) {
        Entry entry = entries.get(code);
        if (entry == null) {
            throw new IllegalStateException("no configuration for supplier: " + code);
        }
        return entry;
    }

    public record Entry(String baseUrl, String apiKey) {
    }
}
