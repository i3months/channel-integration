package com.stayhub.adapter.out.supplier.a;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.stayhub.adapter.out.supplier.Fixtures;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.CatalogRoomType;
import com.stayhub.domain.DailyRate;
import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SupplierAMapperTest {

    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    private final SupplierAMapper mapper = new SupplierAMapper(Fixtures.OBJECT_MAPPER);

    private static JsonNode items(String json) throws Exception {
        return Fixtures.OBJECT_MAPPER.readTree(json).get("items");
    }

    private static Offer offerFor(List<Offer> offers, String hotelCode) {
        return offers.stream().filter(offer -> offer.supplierHotelCode().equals(hotelCode)).findFirst().orElseThrow();
    }

    @Test
    void T05_첫_항목은_일별_단가와_세액을_합산해_총액_429000_이고_예약_가능_1() throws Exception {
        List<Offer> offers = mapper.toOffers(items(Fixtures.read("supplier-a/availability.json")), SEP_1_TO_4);

        Offer riverside = offerFor(offers, "A-10023");
        assertThat(riverside.supplier()).isEqualTo(SupplierCode.A);
        assertThat(riverside.supplierRoomTypeCode()).isEqualTo("DLX-TWN");
        assertThat(riverside.availableRooms()).isEqualTo(1);
        assertThat(riverside.price().totalAmount()).isEqualTo(429_000L);
        assertThat(riverside.price().currency()).isEqualTo("KRW");
        assertThat(riverside.price().breakfastIncluded()).isFalse();
        assertThat(riverside.price().nights()).isEqualTo(3);
        assertThat(riverside.price().daily()).containsExactly(
                new DailyRate(LocalDate.of(2026, 9, 1), 120_000L, 12_000L),
                new DailyRate(LocalDate.of(2026, 9, 2), 150_000L, 15_000L),
                new DailyRate(LocalDate.of(2026, 9, 3), 120_000L, 12_000L));
    }

    @Test
    void T06_둘째_항목은_중간_날짜_재고가_0_이라_예약_가능_0() throws Exception {
        List<Offer> offers = mapper.toOffers(items(Fixtures.read("supplier-a/availability.json")), SEP_1_TO_4);

        assertThat(offers).hasSize(2);
        assertThat(offerFor(offers, "A-10044").availableRooms()).isZero();
    }

    @Test
    void T07_roomTypeCode_가_없는_항목만_빠지고_나머지는_반환한다() throws Exception {
        String json = Fixtures.read("supplier-a/availability.json")
                .replace("\"roomTypeCode\": \"STD-DBL\",", "");

        List<Offer> offers = mapper.toOffers(items(json), SEP_1_TO_4);

        assertThat(offers).extracting(Offer::supplierHotelCode).containsExactly("A-10023");
    }

    @Test
    void AD05_항목_필드의_타입이_틀려도_그_항목만_빠진다() throws Exception {
        String json = Fixtures.read("supplier-a/availability.json")
                .replace("\"remainingRooms\": 2,", "\"remainingRooms\": \"two\",");

        List<Offer> offers = mapper.toOffers(items(json), SEP_1_TO_4);

        assertThat(offers).extracting(Offer::supplierHotelCode).containsExactly("A-10023");
    }

    @Test
    void SA03_currency_나_dailyRates_가_없으면_그_항목은_빠진다() throws Exception {
        String noCurrency = Fixtures.read("supplier-a/availability.json").replaceFirst("\"currency\": \"KRW\",", "");
        String noDailyRates = """
                {"items":[{"hotelCode":"A-1","roomTypeCode":"R","currency":"KRW"}]}""";

        assertThat(mapper.toOffers(items(noCurrency), SEP_1_TO_4))
                .extracting(Offer::supplierHotelCode).containsExactly("A-10044");
        assertThat(mapper.toOffers(items(noDailyRates), SEP_1_TO_4)).isEmpty();
    }

    @Test
    void SA03_breakfastIncluded_가_없으면_false() throws Exception {
        String json = """
                {"items":[{"hotelCode":"A-1","roomTypeCode":"R","currency":"KRW",
                  "dailyRates":[{"date":"2026-09-01","remainingRooms":1,"nightlyRate":100,"taxAmount":10}]}]}""";
        var oneNight = new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 2, 0);

        Offer offer = mapper.toOffers(items(json), oneNight).get(0);

        assertThat(offer.price().breakfastIncluded()).isFalse();
        assertThat(offer.price().totalAmount()).isEqualTo(110L);
    }

    @Test
    void SA04_요청_숙박일이_빠진_항목은_예약_가능_0_이고_총액과_일별_내역은_있는_날짜만() throws Exception {
        String json = """
                {"items":[{"hotelCode":"A-1","roomTypeCode":"R","currency":"KRW","breakfastIncluded":true,
                  "dailyRates":[
                    {"date":"2026-09-03","remainingRooms":2,"nightlyRate":300,"taxAmount":30},
                    {"date":"2026-09-01","remainingRooms":2,"nightlyRate":100,"taxAmount":10}]}]}""";

        Offer offer = mapper.toOffers(items(json), SEP_1_TO_4).get(0);

        assertThat(offer.availableRooms()).isZero();
        assertThat(offer.price().totalAmount()).isEqualTo(440L);
        assertThat(offer.price().daily()).extracting(DailyRate::date)
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));
    }

    @Test
    void SA04_요청_기간_밖의_날짜는_총액과_일별_내역에_넣지_않는다() throws Exception {
        String json = """
                {"items":[{"hotelCode":"A-1","roomTypeCode":"R","currency":"KRW",
                  "dailyRates":[
                    {"date":"2026-09-01","remainingRooms":2,"nightlyRate":100,"taxAmount":10},
                    {"date":"2026-09-02","remainingRooms":2,"nightlyRate":999,"taxAmount":99}]}]}""";
        var oneNight = new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 2, 0);

        Offer offer = mapper.toOffers(items(json), oneNight).get(0);

        assertThat(offer.price().totalAmount()).isEqualTo(110L);
        assertThat(offer.price().daily()).hasSize(1);
    }

    @Test
    void SA02_목록_응답을_CatalogEntry_로_바꾼다() throws Exception {
        List<CatalogEntry> catalog = mapper.toCatalog(items(Fixtures.read("supplier-a/hotels.json")));

        assertThat(catalog).containsExactly(
                new CatalogEntry("A-10023", "Riverside Hotel Seoul",
                        List.of(new CatalogRoomType("DLX-TWN", "Deluxe Twin", 2))),
                new CatalogEntry("A-10044", "Namsan Garden Stay",
                        List.of(new CatalogRoomType("STD-DBL", "Standard Double", 2))));
    }

    @Test
    void SA02_hotelName_이_없는_숙소는_빠지고_roomTypes_가_없으면_빈_목록() throws Exception {
        String json = """
                {"items":[
                  {"hotelCode":"A-1"},
                  {"hotelCode":"A-2","hotelName":"No Rooms"}]}""";

        List<CatalogEntry> catalog = mapper.toCatalog(items(json));

        assertThat(catalog).containsExactly(new CatalogEntry("A-2", "No Rooms", List.of()));
    }

    @Test
    void SA02_필수_필드가_빠진_객실_타입만_빠진다() throws Exception {
        String json = """
                {"items":[{"hotelCode":"A-1","hotelName":"Hotel","roomTypes":[
                  {"roomTypeCode":"OK","roomTypeName":"Ok Room","maxOccupancy":2},
                  {"roomTypeCode":"NO-OCC","roomTypeName":"Broken"}]}]}""";

        List<CatalogEntry> catalog = mapper.toCatalog(items(json));

        assertThat(catalog.get(0).roomTypes()).extracting(CatalogRoomType::supplierRoomTypeCode).containsExactly("OK");
    }
}
