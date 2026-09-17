package com.stayhub.adapter.out.supplier.b;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.stayhub.adapter.out.supplier.Fixtures;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.CatalogRoomType;
import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplierBMapperTest {

    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    private final SupplierBMapper mapper = new SupplierBMapper(Fixtures.OBJECT_MAPPER);

    private static JsonNode items(String json) throws Exception {
        return Fixtures.OBJECT_MAPPER.readTree(json).path("data").path("items");
    }

    @Test
    void T08_총액을_그대로_쓰고_예약_가능_1_조식_포함_일별_내역_null() throws Exception {
        List<Offer> offers = mapper.toOffers(items(Fixtures.read("supplier-b/search.json")), SEP_1_TO_4);

        assertThat(offers).hasSize(1);
        Offer offer = offers.get(0);
        assertThat(offer.supplier()).isEqualTo(SupplierCode.B);
        assertThat(offer.supplierHotelCode()).isEqualTo("B77120");
        assertThat(offer.supplierRoomTypeCode()).isEqualTo("R-401");
        assertThat(offer.availableRooms()).isEqualTo(1);
        assertThat(offer.price().totalAmount()).isEqualTo(452_000L);
        assertThat(offer.price().currency()).isEqualTo("KRW");
        assertThat(offer.price().breakfastIncluded()).isTrue();
        assertThat(offer.price().nights()).isEqualTo(3);
        assertThat(offer.price().daily()).isNull();
    }

    @Test
    void SB03_필수_필드가_빠지거나_타입이_틀린_항목만_빠진다() throws Exception {
        String json = """
                {"resultCode":"0000","data":{"items":[
                  {"propertyId":"P-1","roomId":"R","currency":"KRW","totalPrice":100,
                   "inventory":[{"date":"2026-09-01","remainingRooms":1}]},
                  {"propertyId":"P-2","roomId":"R","currency":"KRW",
                   "inventory":[{"date":"2026-09-01","remainingRooms":1}]},
                  {"propertyId":"P-3","roomId":"R","currency":"KRW","totalPrice":"expensive",
                   "inventory":[{"date":"2026-09-01","remainingRooms":1}]},
                  {"propertyId":"P-4","currency":"KRW","totalPrice":100,
                   "inventory":[{"date":"2026-09-01","remainingRooms":1}]}]}}""";
        var oneNight = new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 2, 0);

        List<Offer> offers = mapper.toOffers(items(json), oneNight);

        assertThat(offers).extracting(Offer::supplierHotelCode).containsExactly("P-1");
        assertThat(offers.get(0).price().breakfastIncluded()).isFalse();
    }

    @Test
    void SB04_인벤토리에_요청_숙박일이_없으면_예약_가능_0() throws Exception {
        String json = Fixtures.read("supplier-b/search.json")
                .replace("{ \"date\": \"2026-09-02\", \"remainingRooms\": 1 },", "");

        Offer offer = mapper.toOffers(items(json), SEP_1_TO_4).get(0);

        assertThat(offer.availableRooms()).isZero();
    }

    @Test
    void SB02_목록_응답을_CatalogEntry_로_바꾼다() throws Exception {
        List<CatalogEntry> catalog = mapper.toCatalog(items(Fixtures.read("supplier-b/properties.json")));

        assertThat(catalog).containsExactly(new CatalogEntry("B77120", "Riverside Hotel Seoul",
                List.of(new CatalogRoomType("R-401", "Deluxe Twin Room", 2))));
    }

    @Test
    void SB02_필수_필드가_빠진_숙소와_객실_타입은_빠진다() throws Exception {
        String json = """
                {"data":{"items":[
                  {"propertyName":"No Id"},
                  {"propertyId":"P-1","propertyName":"Hotel","rooms":[
                    {"roomId":"OK","roomName":"Ok","maxOccupancy":2},
                    {"roomId":"BROKEN","maxOccupancy":2}]}]}}""";

        List<CatalogEntry> catalog = mapper.toCatalog(items(json));

        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).roomTypes()).extracting(CatalogRoomType::supplierRoomTypeCode).containsExactly("OK");
    }
}
