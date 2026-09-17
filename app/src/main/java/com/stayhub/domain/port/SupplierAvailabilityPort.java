package com.stayhub.domain.port;

import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierResult;
import java.util.List;
import reactor.core.publisher.Mono;

public interface SupplierAvailabilityPort {

    /**
     * 숙소 코드 묶음의 재고와 요금. supplierHotelCodes 는 1개 이상 50개 이하이며 호출자가 보장한다.
     * 예외를 던지지 않으며 Mono 는 onError 로 끝나지 않는다.
     */
    Mono<SupplierResult<List<Offer>>> fetchAvailability(List<String> supplierHotelCodes, StaySearchCriteria criteria);
}
