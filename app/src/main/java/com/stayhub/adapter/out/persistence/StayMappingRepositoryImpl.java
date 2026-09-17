package com.stayhub.adapter.out.persistence;

import com.stayhub.domain.RoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.port.StayMappingRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매핑 저장소 구현. 엔티티는 이 패키지 밖으로 나가지 않고 도메인 record 로 바꿔 반환한다.
 * 삭제는 없다. 비활성화만 하므로 내부 식별자는 재사용되지 않는다.
 * 비활성화는 벌크 UPDATE 대신 엔티티를 읽어 바꾼다. @PreUpdate 로 updated_at 을 채우기 위함이며, 숙소 수가 매핑 규모라 부담이 없다.
 */
@Repository
@Transactional(readOnly = true)
public class StayMappingRepositoryImpl implements StayMappingRepository {

    private final SupplierStayJpaRepository stays;
    private final SupplierRoomTypeJpaRepository roomTypes;

    public StayMappingRepositoryImpl(SupplierStayJpaRepository stays, SupplierRoomTypeJpaRepository roomTypes) {
        this.stays = stays;
        this.roomTypes = roomTypes;
    }

    @Override
    public List<Stay> findActiveStays(SupplierCode supplier) {
        return stays.findBySupplierCodeAndActiveTrueOrderByIdAsc(supplier).stream()
                .map(SupplierStayEntity::toDomain)
                .toList();
    }

    @Override
    public List<Stay> findAllActiveStays() {
        return stays.findByActiveTrueOrderByIdAsc().stream()
                .map(SupplierStayEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<Stay> findStay(SupplierCode supplier, String supplierHotelCode) {
        return stays.findBySupplierCodeAndSupplierHotelCode(supplier, supplierHotelCode)
                .map(SupplierStayEntity::toDomain);
    }

    @Override
    public List<RoomType> findActiveRoomTypes(long stayId) {
        return roomTypes.findByStayIdAndActiveTrueOrderByIdAsc(stayId).stream()
                .map(SupplierRoomTypeEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<RoomType> findRoomType(long stayId, String supplierRoomTypeCode) {
        return roomTypes.findByStayIdAndSupplierRoomTypeCode(stayId, supplierRoomTypeCode)
                .map(SupplierRoomTypeEntity::toDomain);
    }

    @Override
    @Transactional
    public Stay upsertStay(SupplierCode supplier, String supplierHotelCode, String name) {
        SupplierStayEntity entity = stays.findBySupplierCodeAndSupplierHotelCode(supplier, supplierHotelCode)
                .map(existing -> {
                    existing.refresh(name);
                    return existing;
                })
                .orElseGet(() -> stays.save(new SupplierStayEntity(supplier, supplierHotelCode, name)));
        stays.flush();
        return entity.toDomain();
    }

    @Override
    @Transactional
    public RoomType upsertRoomType(long stayId, String supplierRoomTypeCode, String name, int maxOccupancy) {
        SupplierRoomTypeEntity entity = roomTypes.findByStayIdAndSupplierRoomTypeCode(stayId, supplierRoomTypeCode)
                .map(existing -> {
                    existing.refresh(name, maxOccupancy);
                    return existing;
                })
                .orElseGet(() -> {
                    SupplierStayEntity stay = stays.findById(stayId)
                            .orElseThrow(() -> new IllegalArgumentException("unknown stay id: " + stayId));
                    return roomTypes.save(new SupplierRoomTypeEntity(stay, supplierRoomTypeCode, name, maxOccupancy));
                });
        roomTypes.flush();
        return entity.toDomain();
    }

    @Override
    @Transactional
    public int deactivateStaysNotIn(SupplierCode supplier, Set<String> supplierHotelCodes) {
        List<SupplierStayEntity> targets = stays.findBySupplierCodeAndActiveTrueOrderByIdAsc(supplier).stream()
                .filter(stay -> !supplierHotelCodes.contains(stay.getSupplierHotelCode()))
                .toList();
        for (SupplierStayEntity stay : targets) {
            stay.deactivate();
            roomTypes.findByStayIdAndActiveTrueOrderByIdAsc(stay.getId()).forEach(SupplierRoomTypeEntity::deactivate);
        }
        return targets.size();
    }

    @Override
    @Transactional
    public int deactivateRoomTypesNotIn(long stayId, Set<String> supplierRoomTypeCodes) {
        List<SupplierRoomTypeEntity> targets = roomTypes.findByStayIdAndActiveTrueOrderByIdAsc(stayId).stream()
                .filter(roomType -> !supplierRoomTypeCodes.contains(roomType.getSupplierRoomTypeCode()))
                .toList();
        targets.forEach(SupplierRoomTypeEntity::deactivate);
        return targets.size();
    }

    @Override
    public Map<Long, Stay> findStaysByIds(Collection<Long> ids) {
        return stays.findAllById(ids).stream()
                .map(SupplierStayEntity::toDomain)
                .collect(Collectors.toMap(Stay::id, stay -> stay, (left, right) -> left, LinkedHashMap::new));
    }

    @Override
    public Map<Long, List<RoomType>> findActiveRoomTypesByStayIds(Collection<Long> stayIds) {
        if (stayIds.isEmpty()) {
            return Map.of();
        }
        return roomTypes.findByStayIdInAndActiveTrueOrderByIdAsc(stayIds).stream()
                .map(SupplierRoomTypeEntity::toDomain)
                .collect(Collectors.groupingBy(RoomType::stayId, LinkedHashMap::new, Collectors.toList()));
    }
}
