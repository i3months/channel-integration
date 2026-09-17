package com.stayhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import com.stayhub.domain.port.SupplierAdapter;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class SupplierRegistryTest {

    private record StubAdapter(SupplierCode supplierCode) implements SupplierAdapter {

        @Override
        public Mono<SupplierResult<List<CatalogEntry>>> fetchCatalog() {
            return Mono.just(SupplierResult.success(List.of()));
        }

        @Override
        public Mono<SupplierResult<List<Offer>>> fetchAvailability(List<String> codes, StaySearchCriteria criteria) {
            return Mono.just(SupplierResult.success(List.of()));
        }
    }

    @Test
    void T13_같은_공급사_코드의_어댑터가_둘이면_생성_시_IllegalStateException() {
        var adapters = List.<SupplierAdapter>of(new StubAdapter(SupplierCode.A), new StubAdapter(SupplierCode.A));

        assertThatThrownBy(() -> new SupplierRegistry(adapters)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void AD06_공급사_코드로_어댑터를_찾는다() {
        var a = new StubAdapter(SupplierCode.A);
        var registry = new SupplierRegistry(List.of(a));

        assertThat(registry.find(SupplierCode.A)).containsSame(a);
        assertThat(registry.find(SupplierCode.B)).isEmpty();
    }

    @Test
    void AD06_all_은_주입_순서와_무관하게_공급사_코드_순서로_정렬한다() {
        var a = new StubAdapter(SupplierCode.A);
        var b = new StubAdapter(SupplierCode.B);

        var registry = new SupplierRegistry(List.of(b, a));

        assertThat(registry.all()).containsExactly(a, b);
    }
}
