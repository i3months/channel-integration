package com.stayhub.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stayhub.application.SearchFailure;
import com.stayhub.application.SearchItem;
import com.stayhub.application.SearchResult;
import com.stayhub.domain.DailyRate;
import com.stayhub.domain.Price;
import java.time.LocalDate;
import java.util.List;

/**
 * 검색 응답 본문. 도메인 record 를 그대로 직렬화하지 않고 이 형태로 옮긴다 (API-04).
 */
public record SearchResponse(
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int adults,
        int children,
        List<Item> items,
        List<Failure> failures) {

    static SearchResponse from(SearchResult result) {
        var criteria = result.criteria();
        return new SearchResponse(
                criteria.checkIn(),
                criteria.checkOut(),
                criteria.nights(),
                criteria.adults(),
                criteria.children(),
                result.items().stream().map(Item::from).toList(),
                result.failures().stream().map(Failure::from).toList());
    }

    public record Item(
            long stayId,
            String stayName,
            long roomTypeId,
            String roomTypeName,
            int maxOccupancy,
            int availableRooms,
            String supplier,
            PriceBody price) {

        static Item from(SearchItem item) {
            return new Item(item.stayId(), item.stayName(), item.roomTypeId(), item.roomTypeName(),
                    item.maxOccupancy(), item.availableRooms(), item.supplier().name(), PriceBody.from(item.price()));
        }
    }

    /**
     * daily 는 날짜별 요금을 주지 않는 공급사면 null 이다. 필드를 생략하지 않고 null 로 내보내
     * 클라이언트가 "일별 내역 없음"을 구분할 수 있게 한다.
     */
    public record PriceBody(
            long totalAmount,
            String currency,
            boolean breakfastIncluded,
            int nights,
            @JsonInclude(JsonInclude.Include.ALWAYS) List<DailyRateBody> daily) {

        static PriceBody from(Price price) {
            List<DailyRateBody> daily = price.daily() == null
                    ? null
                    : price.daily().stream().map(DailyRateBody::from).toList();
            return new PriceBody(price.totalAmount(), price.currency(), price.breakfastIncluded(), price.nights(), daily);
        }
    }

    public record DailyRateBody(LocalDate date, long nightlyRate, long taxAmount) {

        static DailyRateBody from(DailyRate rate) {
            return new DailyRateBody(rate.date(), rate.nightlyRate(), rate.taxAmount());
        }
    }

    public record Failure(String supplier, String reason, String message) {

        static Failure from(SearchFailure failure) {
            return new Failure(failure.supplier().name(), failure.reason().name(), failure.message());
        }
    }
}
