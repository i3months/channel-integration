package com.stayhub.adapter.out.supplier.b;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayhub.domain.AvailabilityRule;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.CatalogRoomType;
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
 * Supplier B 응답 항목을 표준 형태로 바꾼다.
 * B 는 요청 기간 전체의 세금 포함 총액만 주므로 그대로 쓰고, 날짜별 요금이 없어 daily 는 null 이다.
 * taxIncluded 는 항상 true 라 읽지 않는다. 항목 하나가 깨지면 그 항목만 버린다 (AD-05).
 */
@Component
public class SupplierBMapper {

    private static final Logger log = LoggerFactory.getLogger(SupplierBMapper.class);

    private final ObjectMapper objectMapper;

    public SupplierBMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<CatalogEntry> toCatalog(JsonNode items) {
        List<CatalogEntry> entries = new ArrayList<>();
        for (JsonNode node : items) {
            read(node, SupplierBDtos.Property.class, "catalog")
                    .flatMap(property -> toCatalogEntry(property, node))
                    .ifPresent(entries::add);
        }
        return entries;
    }

    public List<Offer> toOffers(JsonNode items, StaySearchCriteria criteria) {
        List<Offer> offers = new ArrayList<>();
        for (JsonNode node : items) {
            read(node, SupplierBDtos.SearchItem.class, "availability")
                    .flatMap(item -> toOffer(item, criteria, node))
                    .ifPresent(offers::add);
        }
        return offers;
    }

    private Optional<CatalogEntry> toCatalogEntry(SupplierBDtos.Property property, JsonNode node) {
        if (property.propertyId() == null || property.propertyName() == null) {
            return dropped("catalog", node, "propertyId or propertyName missing");
        }
        List<CatalogRoomType> roomTypes = new ArrayList<>();
        if (property.rooms() != null) {
            for (JsonNode roomNode : property.rooms()) {
                read(roomNode, SupplierBDtos.PropertyRoom.class, "catalog")
                        .flatMap(room -> toCatalogRoomType(room, roomNode))
                        .ifPresent(roomTypes::add);
            }
        }
        return Optional.of(new CatalogEntry(property.propertyId(), property.propertyName(), roomTypes));
    }

    private Optional<CatalogRoomType> toCatalogRoomType(SupplierBDtos.PropertyRoom room, JsonNode node) {
        if (room.roomId() == null || room.roomName() == null || room.maxOccupancy() == null) {
            return dropped("catalog", node, "roomId, roomName or maxOccupancy missing");
        }
        return Optional.of(new CatalogRoomType(room.roomId(), room.roomName(), room.maxOccupancy()));
    }

    private Optional<Offer> toOffer(SupplierBDtos.SearchItem item, StaySearchCriteria criteria, JsonNode node) {
        if (item.propertyId() == null || item.roomId() == null || item.currency() == null
                || item.totalPrice() == null || item.inventory() == null) {
            return dropped("availability", node, "propertyId, roomId, currency, totalPrice or inventory missing");
        }
        Map<LocalDate, Integer> remainingByDate = new HashMap<>();
        for (SupplierBDtos.Inventory inventory : item.inventory()) {
            if (inventory == null || inventory.date() == null || inventory.remainingRooms() == null) {
                return dropped("availability", node, "inventory entry has missing field");
            }
            remainingByDate.put(inventory.date(), inventory.remainingRooms());
        }
        int availableRooms = AvailabilityRule.availableRooms(criteria.stayDates(), remainingByDate);
        boolean breakfastIncluded = Boolean.TRUE.equals(item.breakfastIncluded());
        Price price = new Price(item.totalPrice(), item.currency(), breakfastIncluded, criteria.nights(), null);
        return Optional.of(new Offer(SupplierCode.B, item.propertyId(), item.roomId(), availableRooms, price));
    }

    private <T> Optional<T> read(JsonNode node, Class<T> type, String api) {
        try {
            return Optional.of(objectMapper.treeToValue(node, type));
        } catch (Exception e) {
            return dropped(api, node, e.getClass().getSimpleName());
        }
    }

    private <T> Optional<T> dropped(String api, JsonNode node, String reason) {
        String code = node.path("propertyId").asText("") + "/" + node.path("roomId").asText("");
        log.warn("event=item_dropped supplier=B api={} code={} reason=\"{}\"", api, code, reason);
        return Optional.empty();
    }
}
