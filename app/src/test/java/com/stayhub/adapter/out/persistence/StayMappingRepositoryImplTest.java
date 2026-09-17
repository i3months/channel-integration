package com.stayhub.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stayhub.domain.RoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.SupplierCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import(StayMappingRepositoryImpl.class)
class StayMappingRepositoryImplTest {

    @Autowired
    StayMappingRepositoryImpl repository;

    @Autowired
    SupplierStayJpaRepository stayJpa;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void T30_같은_코드로_두_번_upsert_하면_id_는_같고_이름은_두_번째_값() {
        Stay first = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        Stay second = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul (Renewed)");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.name()).isEqualTo("Riverside Hotel Seoul (Renewed)");
        assertThat(repository.findStay(SupplierCode.A, "A-10023")).get()
                .extracting(Stay::name).isEqualTo("Riverside Hotel Seoul (Renewed)");
    }

    @Test
    void T31_집합에_없는_숙소와_그_객실_타입을_비활성화한다() {
        Stay kept = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        Stay dropped = repository.upsertStay(SupplierCode.A, "A-10044", "Namsan Garden Stay");
        repository.upsertRoomType(kept.id(), "DLX-TWN", "Deluxe Twin", 2);
        RoomType droppedRoom = repository.upsertRoomType(dropped.id(), "STD-DBL", "Standard Double", 2);

        int deactivated = repository.deactivateStaysNotIn(SupplierCode.A, Set.of("A-10023"));

        assertThat(deactivated).isEqualTo(1);
        assertThat(repository.findActiveStays(SupplierCode.A)).extracting(Stay::supplierHotelCode)
                .containsExactly("A-10023");
        assertThat(repository.findRoomType(dropped.id(), "STD-DBL")).get()
                .extracting(RoomType::active).isEqualTo(false);
        assertThat(repository.findActiveRoomTypes(dropped.id())).isEmpty();
        assertThat(repository.findRoomType(dropped.id(), "STD-DBL")).get()
                .extracting(RoomType::id).isEqualTo(droppedRoom.id());
    }

    @Test
    void T32_같은_공급사와_숙소_코드를_직접_두_번_넣으면_유일_제약_위반() {
        stayJpa.saveAndFlush(new SupplierStayEntity(SupplierCode.A, "A-10023", "Riverside Hotel Seoul"));

        assertThatThrownBy(() -> stayJpa.saveAndFlush(
                new SupplierStayEntity(SupplierCode.A, "A-10023", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void T33_비활성_숙소는_active_조회에_안_나오고_단건_조회에는_나온다() {
        repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        repository.deactivateStaysNotIn(SupplierCode.A, Set.of());

        assertThat(repository.findActiveStays(SupplierCode.A)).isEmpty();
        assertThat(repository.findAllActiveStays()).isEmpty();
        assertThat(repository.findStay(SupplierCode.A, "A-10023")).get()
                .extracting(Stay::active).isEqualTo(false);
    }

    @Test
    void MP_공급사가_다르면_같은_숙소_코드여도_별개_숙소다() {
        Stay a = repository.upsertStay(SupplierCode.A, "SAME-CODE", "Hotel A");
        Stay b = repository.upsertStay(SupplierCode.B, "SAME-CODE", "Hotel B");

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(repository.findAllActiveStays()).hasSize(2);
        assertThat(repository.findActiveStays(SupplierCode.B)).extracting(Stay::name).containsExactly("Hotel B");
    }

    @Test
    void MP_비활성_숙소를_다시_upsert_하면_같은_id_로_재활성화된다() {
        Stay original = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        repository.deactivateStaysNotIn(SupplierCode.A, Set.of());

        Stay revived = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");

        assertThat(revived.id()).isEqualTo(original.id());
        assertThat(revived.active()).isTrue();
    }

    @Test
    void MP_객실_타입_upsert_는_숙소_안에서_코드로_식별하고_이름과_인원을_갱신한다() {
        Stay stay1 = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        Stay stay2 = repository.upsertStay(SupplierCode.A, "A-10044", "Namsan Garden Stay");

        RoomType first = repository.upsertRoomType(stay1.id(), "DLX-TWN", "Deluxe Twin", 2);
        RoomType updated = repository.upsertRoomType(stay1.id(), "DLX-TWN", "Deluxe Twin Large", 3);
        RoomType otherStaySameCode = repository.upsertRoomType(stay2.id(), "DLX-TWN", "Deluxe Twin", 2);

        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.name()).isEqualTo("Deluxe Twin Large");
        assertThat(updated.maxOccupancy()).isEqualTo(3);
        assertThat(updated.stayId()).isEqualTo(stay1.id());
        assertThat(otherStaySameCode.id()).isNotEqualTo(first.id());
    }

    @Test
    void MP_집합에_없는_객실_타입만_비활성화한다() {
        Stay stay = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        repository.upsertRoomType(stay.id(), "DLX-TWN", "Deluxe Twin", 2);
        repository.upsertRoomType(stay.id(), "STD-DBL", "Standard Double", 2);

        int deactivated = repository.deactivateRoomTypesNotIn(stay.id(), Set.of("DLX-TWN"));

        assertThat(deactivated).isEqualTo(1);
        assertThat(repository.findActiveRoomTypes(stay.id())).extracting(RoomType::supplierRoomTypeCode)
                .containsExactly("DLX-TWN");
    }

    @Test
    void MP_id_목록으로_숙소와_active_객실_타입을_한_번에_조회한다() {
        Stay stay1 = repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");
        Stay stay2 = repository.upsertStay(SupplierCode.B, "B77120", "Riverside Hotel Seoul");
        repository.upsertRoomType(stay1.id(), "DLX-TWN", "Deluxe Twin", 2);
        repository.upsertRoomType(stay1.id(), "STD-DBL", "Standard Double", 2);
        repository.upsertRoomType(stay2.id(), "R-401", "Deluxe Twin Room", 2);
        repository.deactivateRoomTypesNotIn(stay1.id(), Set.of("DLX-TWN"));

        var stays = repository.findStaysByIds(List.of(stay1.id(), stay2.id()));
        var roomTypes = repository.findActiveRoomTypesByStayIds(List.of(stay1.id(), stay2.id()));

        assertThat(stays).containsOnlyKeys(stay1.id(), stay2.id());
        assertThat(roomTypes.get(stay1.id())).extracting(RoomType::supplierRoomTypeCode).containsExactly("DLX-TWN");
        assertThat(roomTypes.get(stay2.id())).extracting(RoomType::supplierRoomTypeCode).containsExactly("R-401");
    }

    @Test
    void MP04_생성_시각과_수정_시각이_채워진다() {
        repository.upsertStay(SupplierCode.A, "A-10023", "Riverside Hotel Seoul");

        SupplierStayEntity entity = stayJpa.findBySupplierCodeAndSupplierHotelCode(SupplierCode.A, "A-10023").orElseThrow();

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void MP01_supplier_code_컬럼은_DB_enum_이_아니라_VARCHAR_16_이다() {
        var column = jdbc.queryForMap("""
                SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'SUPPLIER_STAY' AND COLUMN_NAME = 'SUPPLIER_CODE'
                """);

        assertThat(column.get("DATA_TYPE")).isEqualTo("CHARACTER VARYING");
        assertThat(((Number) column.get("CHARACTER_MAXIMUM_LENGTH")).intValue()).isEqualTo(16);
    }
}
