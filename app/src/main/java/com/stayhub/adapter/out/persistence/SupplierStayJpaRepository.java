package com.stayhub.adapter.out.persistence;

import com.stayhub.domain.SupplierCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierStayJpaRepository extends JpaRepository<SupplierStayEntity, Long> {

    Optional<SupplierStayEntity> findBySupplierCodeAndSupplierHotelCode(SupplierCode supplierCode, String supplierHotelCode);

    List<SupplierStayEntity> findBySupplierCodeAndActiveTrueOrderByIdAsc(SupplierCode supplierCode);

    List<SupplierStayEntity> findByActiveTrueOrderByIdAsc();
}
