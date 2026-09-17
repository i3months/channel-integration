package com.stayhub.domain.port;

import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.SupplierResult;
import java.util.List;
import reactor.core.publisher.Mono;

public interface SupplierCatalogPort {

    /**
     * 공급사가 취급하는 전체 숙소와 객실 타입 목록. 예외를 던지지 않으며 Mono 는 onError 로 끝나지 않는다.
     */
    Mono<SupplierResult<List<CatalogEntry>>> fetchCatalog();
}
