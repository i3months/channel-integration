package com.stayhub.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.adapter.out.persistence.StayMappingRepositoryImpl;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.CatalogRoomType;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.RoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import reactor.core.publisher.Mono;

@DataJpaTest
@Import({StayMappingRepositoryImpl.class, CatalogMappingWriter.class})
class SyncSupplierCatalogServiceTest {

    private static final CatalogEntry RIVERSIDE = new CatalogEntry("A-10023", "Riverside Hotel Seoul",
            List.of(new CatalogRoomType("DLX-TWN", "Deluxe Twin", 2)));
    private static final CatalogEntry NAMSAN = new CatalogEntry("A-10044", "Namsan Garden Stay",
            List.of(new CatalogRoomType("STD-DBL", "Standard Double", 2)));

    @Autowired
    StayMappingRepositoryImpl repository;

    @Autowired
    CatalogMappingWriter writer;

    private FakeSupplierAdapter supplierA;
    private SyncSupplierCatalogService service;

    @BeforeEach
    void setUp() {
        supplierA = new FakeSupplierAdapter(SupplierCode.A);
        service = new SyncSupplierCatalogService(new SupplierRegistry(List.of(supplierA)), writer, Duration.ofSeconds(1));
    }

    @Test
    void T40_첫_동기화는_모두_추가이고_같은_목록으로_다시_하면_모두_갱신이며_id_가_같다() {
        supplierA.catalogReturns(List.of(RIVERSIDE, NAMSAN));

        SyncResult first = service.sync(SupplierCode.A);
        List<Long> firstIds = repository.findActiveStays(SupplierCode.A).stream().map(Stay::id).toList();
        SyncResult second = service.sync(SupplierCode.A);
        List<Long> secondIds = repository.findActiveStays(SupplierCode.A).stream().map(Stay::id).toList();

        assertThat(first).isEqualTo(new SyncResult(SupplierCode.A, 2, 0, 0, null, null));
        assertThat(second).isEqualTo(new SyncResult(SupplierCode.A, 0, 2, 0, null, null));
        assertThat(secondIds).isEqualTo(firstIds).hasSize(2);
    }

    @Test
    void SY02_객실_타입도_함께_저장한다() {
        supplierA.catalogReturns(List.of(RIVERSIDE));

        service.sync(SupplierCode.A);

        Stay stay = repository.findStay(SupplierCode.A, "A-10023").orElseThrow();
        assertThat(repository.findActiveRoomTypes(stay.id())).extracting(RoomType::supplierRoomTypeCode)
                .containsExactly("DLX-TWN");
    }

    @Test
    void T41_다음_목록에서_숙소가_빠지면_비활성화되고_남은_숙소_id_는_그대로() {
        supplierA.catalogReturns(List.of(RIVERSIDE, NAMSAN));
        service.sync(SupplierCode.A);
        long riversideId = repository.findStay(SupplierCode.A, "A-10023").orElseThrow().id();

        supplierA.catalogReturns(List.of(RIVERSIDE));
        SyncResult result = service.sync(SupplierCode.A);

        assertThat(result.deactivated()).isEqualTo(1);
        assertThat(repository.findStay(SupplierCode.A, "A-10044")).get().extracting(Stay::active).isEqualTo(false);
        assertThat(repository.findStay(SupplierCode.A, "A-10023")).get().extracting(Stay::id).isEqualTo(riversideId);
    }

    @Test
    void SY02_숙소의_객실_타입이_목록에서_빠지면_그_객실_타입만_비활성화된다() {
        supplierA.catalogReturns(List.of(new CatalogEntry("A-10023", "Riverside Hotel Seoul", List.of(
                new CatalogRoomType("DLX-TWN", "Deluxe Twin", 2),
                new CatalogRoomType("STD-DBL", "Standard Double", 2)))));
        service.sync(SupplierCode.A);

        supplierA.catalogReturns(List.of(RIVERSIDE));
        service.sync(SupplierCode.A);

        Stay stay = repository.findStay(SupplierCode.A, "A-10023").orElseThrow();
        assertThat(repository.findActiveRoomTypes(stay.id())).extracting(RoomType::supplierRoomTypeCode)
                .containsExactly("DLX-TWN");
    }

    @Test
    void T42_공급사_호출이_실패하면_실패_사유를_돌려주고_기존_매핑은_그대로() {
        supplierA.catalogReturns(List.of(RIVERSIDE));
        service.sync(SupplierCode.A);

        supplierA.catalogReturns(() -> Mono.just(SupplierResult.failure(FailureReason.UNAVAILABLE, "503")));
        SyncResult result = service.sync(SupplierCode.A);

        assertThat(result).isEqualTo(new SyncResult(SupplierCode.A, 0, 0, 0, FailureReason.UNAVAILABLE, "503"));
        assertThat(repository.findActiveStays(SupplierCode.A)).extracting(Stay::supplierHotelCode)
                .containsExactly("A-10023");
    }

    @Test
    void T43_빈_목록이면_성공이고_그_공급사의_모든_숙소가_비활성화된다() {
        supplierA.catalogReturns(List.of(RIVERSIDE, NAMSAN));
        service.sync(SupplierCode.A);

        supplierA.catalogReturns(List.of());
        SyncResult result = service.sync(SupplierCode.A);

        assertThat(result).isEqualTo(new SyncResult(SupplierCode.A, 0, 0, 2, null, null));
        assertThat(repository.findActiveStays(SupplierCode.A)).isEmpty();
    }

    @Test
    void SY02_목록_응답이_시간_안에_오지_않으면_TIMEOUT_실패로_본다() {
        supplierA.catalogReturns(Mono::never);

        SyncResult result = service.sync(SupplierCode.A);

        assertThat(result.failureReason()).isEqualTo(FailureReason.TIMEOUT);
    }

    @Test
    void SY02_등록되지_않은_공급사면_IllegalArgumentException() {
        assertThatThrownBy(() -> service.sync(SupplierCode.B)).isInstanceOf(IllegalArgumentException.class);
    }
}
