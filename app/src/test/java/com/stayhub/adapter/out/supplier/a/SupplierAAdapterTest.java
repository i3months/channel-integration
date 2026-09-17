package com.stayhub.adapter.out.supplier.a;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.supplier.Fixtures;
import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierCallExecutor;
import com.stayhub.adapter.out.supplier.common.SupplierProperties;
import com.stayhub.adapter.out.supplier.common.WebClientFactory;
import com.stayhub.domain.CatalogEntry;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.Offer;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spring 컨텍스트 없이 실제 WebClient 와 MockWebServer 로 A 어댑터 전체 흐름을 검증한다.
 * 타임아웃과 재시도 대기는 테스트 시간을 줄이려고 짧게 준다.
 */
class SupplierAAdapterTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);
    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);
    private static final List<String> CODES = List.of("A-10023", "A-10044");

    private MockWebServer server;
    private SupplierAAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        var suppliers = new SupplierProperties(Map.of(SupplierCode.A, new SupplierProperties.Entry(baseUrl, "key-a")));
        var retry = new IntegrationProperties.Retry(2, Duration.ofMillis(10));
        var integration = new IntegrationProperties(true, Duration.ofSeconds(1), RESPONSE_TIMEOUT, RESPONSE_TIMEOUT,
                Duration.ofSeconds(5), 50, 4, retry);
        adapter = new SupplierAAdapter(
                new WebClientFactory(suppliers, integration.connectTimeout()),
                new SupplierCallExecutor(retry),
                integration,
                new SupplierAMapper(Fixtures.OBJECT_MAPPER),
                new SupplierAFailureMapper(Fixtures.OBJECT_MAPPER),
                Fixtures.OBJECT_MAPPER);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).addHeader("Content-Type", "application/json").setBody(body);
    }

    private SupplierResult<List<Offer>> search() {
        return adapter.fetchAvailability(CODES, SEP_1_TO_4).block(Duration.ofSeconds(10));
    }

    @Test
    void SA01_공급사_코드는_A() {
        assertThat(adapter.supplierCode()).isEqualTo(SupplierCode.A);
    }

    @Test
    void T20_정상_응답이면_Offer_2건이고_값은_T05_와_같다() {
        server.enqueue(json(200, Fixtures.read("supplier-a/availability.json")));

        SupplierResult<List<Offer>> result = search();

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Success.class, success -> {
            @SuppressWarnings("unchecked")
            List<Offer> offers = (List<Offer>) success.value();
            assertThat(offers).hasSize(2);
            Offer riverside = offers.get(0);
            assertThat(riverside.supplierHotelCode()).isEqualTo("A-10023");
            assertThat(riverside.availableRooms()).isEqualTo(1);
            assertThat(riverside.price().totalAmount()).isEqualTo(429_000L);
            assertThat(riverside.price().breakfastIncluded()).isFalse();
            assertThat(riverside.price().nights()).isEqualTo(3);
            assertThat(riverside.price().daily()).extracting(com.stayhub.domain.DailyRate::date).containsExactly(
                    LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3));
            assertThat(offers.get(1).availableRooms()).isZero();
        });
    }

    @Test
    void T21_503_이면_UNAVAILABLE_이고_재시도_포함_요청_2번() {
        String body = "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"temporarily unavailable\"}";
        server.enqueue(json(503, body));
        server.enqueue(json(503, body));

        SupplierResult<List<Offer>> result = search();

        assertThat(result).isEqualTo(SupplierResult.failure(FailureReason.UNAVAILABLE,
                "503 SERVICE_UNAVAILABLE: temporarily unavailable"));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void T22_400_이면_BAD_REQUEST_이고_재시도_없이_요청_1번() {
        server.enqueue(json(400, "{\"error\":\"INVALID_DATE_RANGE\",\"message\":\"checkOut must be after checkIn\"}"));
        server.enqueue(json(200, Fixtures.read("supplier-a/availability.json")));

        SupplierResult<List<Offer>> result = search();

        assertThat(result).isEqualTo(SupplierResult.failure(FailureReason.BAD_REQUEST,
                "400 INVALID_DATE_RANGE: checkOut must be after checkIn"));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void T25_응답이_오지_않으면_응답_타임아웃으로_끊고_TIMEOUT() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        long started = System.nanoTime();

        SupplierResult<List<Offer>> result = search();

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.TIMEOUT));
        assertThat(elapsedMillis).isLessThan(2_000L);
    }

    @Test
    void T26_첫_응답이_503_이고_두_번째가_200_이면_성공이고_요청_2번() {
        server.enqueue(json(503, "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"temporarily unavailable\"}"));
        server.enqueue(json(200, Fixtures.read("supplier-a/availability.json")));

        SupplierResult<List<Offer>> result = search();

        assertThat(result.isSuccess()).isTrue();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void T27_본문이_JSON_이_아니면_MALFORMED_이고_요청_1번() {
        server.enqueue(json(200, "<html>not json</html>"));
        server.enqueue(json(200, Fixtures.read("supplier-a/availability.json")));

        SupplierResult<List<Offer>> result = search();

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void AD05_본문은_JSON_이지만_items_가_없으면_MALFORMED() {
        server.enqueue(json(200, "{}"));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
    }

    @Test
    void AD05_200_인데_본문이_비어_있으면_MALFORMED() {
        server.enqueue(new MockResponse().setResponseCode(200));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
    }

    @Test
    void T28_재고_요청은_API_키_헤더와_SA03_경로_쿼리로_간다() throws InterruptedException {
        server.enqueue(json(200, Fixtures.read("supplier-a/availability.json")));

        search();

        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getHeader("X-Api-Key")).isEqualTo("key-a");
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo(
                "/a/v1/availability?hotelCodes=A-10023,A-10044&checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0");
    }

    @Test
    void SA02_목록_요청은_hotels_경로로_가고_CatalogEntry_를_반환한다() throws InterruptedException {
        server.enqueue(json(200, Fixtures.read("supplier-a/hotels.json")));

        SupplierResult<List<CatalogEntry>> result = adapter.fetchCatalog().block(Duration.ofSeconds(5));

        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getPath()).isEqualTo("/a/v1/hotels");
        assertThat(request.getHeader("X-Api-Key")).isEqualTo("key-a");
        assertThat(result).isInstanceOfSatisfying(SupplierResult.Success.class,
                success -> assertThat((List<?>) success.value()).hasSize(2));
    }

    @Test
    void SA02_목록_요청도_503_이면_UNAVAILABLE() {
        String body = "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"temporarily unavailable\"}";
        server.enqueue(json(503, body));
        server.enqueue(json(503, body));

        SupplierResult<List<CatalogEntry>> result = adapter.fetchCatalog().block(Duration.ofSeconds(5));

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.UNAVAILABLE));
    }
}
