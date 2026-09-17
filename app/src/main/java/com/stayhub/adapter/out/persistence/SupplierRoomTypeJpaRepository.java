package com.stayhub.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRoomTypeJpaRepository extends JpaRepository<SupplierRoomTypeEntity, Long> {

    Optional<SupplierRoomTypeEntity> findByStayIdAndSupplierRoomTypeCode(Long stayId, String supplierRoomTypeCode);

    List<SupplierRoomTypeEntity> findByStayIdAndActiveTrueOrderByIdAsc(Long stayId);

    List<SupplierRoomTypeEntity> findByStayIdInAndActiveTrueOrderByIdAsc(Collection<Long> stayIds);
}
