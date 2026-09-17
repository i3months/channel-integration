package com.stayhub.application;

import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.CatalogRoomType;
import com.stayhub.domain.Stay;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.port.StayMappingRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받아 온 공급사 목록을 매핑에 반영한다. 공급사 하나의 반영은 하나의 트랜잭션이다.
 * 공급사 호출(수 초)을 트랜잭션 안에서 기다리지 않도록 호출과 반영을 나눴다.
 */
@Service
public class CatalogMappingWriter {

    private final StayMappingRepository repository;

    public CatalogMappingWriter(StayMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SyncResult apply(SupplierCode supplier, List<CatalogEntry> entries) {
        int added = 0;
        int updated = 0;
        Set<String> hotelCodes = new HashSet<>();

        for (CatalogEntry entry : entries) {
            if (repository.findStay(supplier, entry.supplierHotelCode()).isPresent()) {
                updated++;
            } else {
                added++;
            }
            Stay stay = repository.upsertStay(supplier, entry.supplierHotelCode(), entry.name());
            hotelCodes.add(entry.supplierHotelCode());

            Set<String> roomTypeCodes = new HashSet<>();
            for (CatalogRoomType roomType : entry.roomTypes()) {
                repository.upsertRoomType(stay.id(), roomType.supplierRoomTypeCode(), roomType.name(),
                        roomType.maxOccupancy());
                roomTypeCodes.add(roomType.supplierRoomTypeCode());
            }
            repository.deactivateRoomTypesNotIn(stay.id(), roomTypeCodes);
        }

        int deactivated = repository.deactivateStaysNotIn(supplier, hotelCodes);
        return new SyncResult(supplier, added, updated, deactivated, null, null);
    }
}
