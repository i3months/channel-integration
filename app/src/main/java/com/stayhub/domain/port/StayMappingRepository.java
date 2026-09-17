package com.stayhub.domain.port;

import com.stayhub.domain.RoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.SupplierCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 공급사 코드와 내부 식별자 매핑 저장소. 식별자는 재사용하지 않으며 삭제 없이 비활성화만 한다.
 */
public interface StayMappingRepository {

    List<Stay> findActiveStays(SupplierCode supplier);

    List<Stay> findAllActiveStays();

    Optional<Stay> findStay(SupplierCode supplier, String supplierHotelCode);

    List<RoomType> findActiveRoomTypes(long stayId);

    Optional<RoomType> findRoomType(long stayId, String supplierRoomTypeCode);

    /** 있으면 name 을 갱신하고 active=true 로 되돌린다. 없으면 만든다. */
    Stay upsertStay(SupplierCode supplier, String supplierHotelCode, String name);

    /** 있으면 name, maxOccupancy 를 갱신하고 active=true 로 되돌린다. 없으면 만든다. */
    RoomType upsertRoomType(long stayId, String supplierRoomTypeCode, String name, int maxOccupancy);

    /** 해당 공급사의 active 숙소 중 집합에 없는 것과 그 객실 타입을 비활성화하고, 비활성화한 숙소 수를 반환한다. */
    int deactivateStaysNotIn(SupplierCode supplier, Set<String> supplierHotelCodes);

    /** 해당 숙소의 active 객실 타입 중 집합에 없는 것을 비활성화하고 그 수를 반환한다. */
    int deactivateRoomTypesNotIn(long stayId, Set<String> supplierRoomTypeCodes);

    Map<Long, Stay> findStaysByIds(Collection<Long> ids);

    Map<Long, List<RoomType>> findActiveRoomTypesByStayIds(Collection<Long> stayIds);
}
