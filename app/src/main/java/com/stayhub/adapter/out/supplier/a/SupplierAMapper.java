package com.stayhub.adapter.out.supplier.a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.domain.AvailabilityRule;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.CatalogRoomType;
import com.stayhub.domain.DailyRate;
import com.stayhub.domain.Offer;
import com.stayhub.domain.Price;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Supplier A 응답 항목을 표준 형태로 바꾼다.
 * A 는 날짜별 세금 별도 단가와 세액을 주므로, 요청 숙박일의 (단가 + 세액) 합이 세금 포함 총액이다.
 * 항목 하나가 깨지면 그 항목만 버리고 WARN 로그를 남긴다 (AD-05).
 */
@Component
public class SupplierAMapper {

    private static final Logger log = LoggerFactory.getLogger(SupplierAMapper.class);

    private final ObjectMapper objectMapper;

    public SupplierAMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CatalogEntry> toCatalog(JsonNode items) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode node : items) {
            read(node, SupplierADtos.Hotel.class, "catalog")
                    .flatMap(hotel -> toCatalogEntry(hotel, node))
                    .ifPresent(entries::add);
        }
        return entries;
    }

    public List<Offer> toOffers(JsonNode items, StaySearchCriteria criteria) {
        List<Offer> offers = new ArrayList<>();
        for (JsonNode node : items) {
            read(node, SupplierADtos.AvailabilityItem.class, "availability")
                    .flatMap(item -> toOffer(item, criteria, node))
                    .ifPresent(offers::add);
        }
        return offers;
    }

    private Optional<CatalogEntry> toCatalogEntry(SupplierADtos.Hotel hotel, JsonNode node) {
        if (hotel.hotelCode() == null || hotel.hotelName() == null) {
            return dropped("catalog", node, "hotelCode or hotelName missing");
        }
        List<CatalogRoomType> roomTypes = new ArrayList<>();
        if (hotel.roomTypes() != null) {
            for (JsonNode roomNode : hotel.roomTypes()) {
                read(roomNode, SupplierADtos.HotelRoomType.class, "catalog")
                        .flatMap(room -> toCatalogRoomType(room, roomNode))
                        .ifPresent(roomTypes::add);
            }
        }
        return Optional.of(new CatalogEntry(hotel.hotelCode(), hotel.hotelName(), roomTypes));
    }

    private Optional<CatalogRoomType> toCatalogRoomType(SupplierADtos.HotelRoomType room, JsonNode node) {
        if (room.roomTypeCode() == null || room.roomTypeName() == null || room.maxOccupancy() == null) {
            return dropped("catalog", node, "roomTypeCode, roomTypeName or maxOccupancy missing");
        }
        return Optional.of(new CatalogRoomType(room.roomTypeCode(), room.roomTypeName(), room.maxOccupancy()));
    }

    private Optional<Offer> toOffer(SupplierADtos.AvailabilityItem item, StaySearchCriteria criteria, JsonNode node) {
        if (item.hotelCode() == null || item.roomTypeCode() == null || item.currency() == null
                || item.dailyRates() == null) {
            return dropped("availability", node, "hotelCode, roomTypeCode, currency or dailyRates missing");
        }
        Map<LocalDate, SupplierADtos.DailyRate> rateByDate = new HashMap<>();
        for (SupplierADtos.DailyRate rate : item.dailyRates()) {
            if (rate == null || rate.date() == null || rate.remainingRooms() == null
                    || rate.nightlyRate() == null || rate.taxAmount() == null) {
                return dropped("availability", node, "dailyRates entry has missing field");
            }
            rateByDate.put(rate.date(), rate);
        }

        Map<LocalDate, Integer> remainingByDate = new HashMap<>();
        rateByDate.forEach((date, rate) -> remainingByDate.put(date, rate.remainingRooms()));
        int availableRooms = AvailabilityRule.availableRooms(criteria.stayDates(), remainingByDate);

        long totalAmount = 0;
        List<DailyRate> daily = new ArrayList<>();
        for (LocalDate date : criteria.stayDates()) {
            SupplierADtos.DailyRate rate = rateByDate.get(date);
            if (rate == null) {
                continue;
            }
            totalAmount += rate.nightlyRate() + rate.taxAmount();
            daily.add(new DailyRate(date, rate.nightlyRate(), rate.taxAmount()));
        }

        boolean breakfastIncluded = Boolean.TRUE.equals(item.breakfastIncluded());
        Price price = new Price(totalAmount, item.currency(), breakfastIncluded, criteria.nights(), daily);
        return Optional.of(new Offer(SupplierCode.A, item.hotelCode(), item.roomTypeCode(), availableRooms, price));
    }

    private <T> Optional<T> read(JsonNode node, Class<T> type, String api) {
        try {
            return Optional.of(objectMapper.treeToValue(node, type));
        } catch (Exception e) {
            return dropped(api, node, e.getClass().getSimpleName());
        }
    }

    private <T> Optional<T> dropped(String api, JsonNode node, String reason) {
        String code = node.path("hotelCode").asText("") + "/" + node.path("roomTypeCode").asText("");
        log.warn("event=item_dropped supplier=A api={} code={} reason=\"{}\"", api, code, reason);
        return Optional.empty();
    }
}
