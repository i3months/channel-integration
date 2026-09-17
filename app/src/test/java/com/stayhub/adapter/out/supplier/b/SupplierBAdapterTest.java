package com.stayhub.adapter.out.supplier.b;

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

class SupplierBAdapterTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);
    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);
    private static final String E503 =
            "{\"resultCode\":\"E503\",\"resultMessage\":\"TEMPORARILY_UNAVAILABLE\",\"data\":null}";

    private MockWebServer server;
    private SupplierBAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var suppliers = new SupplierProperties(Map.of(
                SupplierCode.B, new SupplierProperties.Entry(server.url("/").toString(), "key-b")));
        var retry = new IntegrationProperties.Retry(2, Duration.ofMillis(10));
        var integration = new IntegrationProperties(true, Duration.ofSeconds(1), RESPONSE_TIMEOUT, RESPONSE_TIMEOUT,
                Duration.ofSeconds(5), 50, 4, retry);
        adapter = new SupplierBAdapter(
                new WebClientFactory(suppliers, integration.connectTimeout()),
                new SupplierCallExecutor(retry),
                integration,
                new SupplierBMapper(Fixtures.OBJECT_MAPPER),
                new SupplierBFailureMapper(),
                Fixtures.OBJECT_MAPPER);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse ok(String body) {
        return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body);
    }

    private SupplierResult<List<Offer>> search() {
        return adapter.fetchAvailability(List.of("B77120"), SEP_1_TO_4).block(Duration.ofSeconds(10));
    }

    @Test
    void SB01_공급사_코드는_B() {
        assertThat(adapter.supplierCode()).isEqualTo(SupplierCode.B);
    }

    @Test
    void SB03_정상_응답이면_Offer_1건() {
        server.enqueue(ok(Fixtures.read("supplier-b/search.json")));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Success.class,
                success -> assertThat((List<?>) success.value()).hasSize(1));
    }

    @Test
    void T23_HTTP_200_이어도_resultCode_E503_이면_UNAVAILABLE_이고_재시도_포함_요청_2번() {
        server.enqueue(ok(E503));
        server.enqueue(ok(E503));

        assertThat(search()).isEqualTo(SupplierResult.failure(FailureReason.UNAVAILABLE, "E503 TEMPORARILY_UNAVAILABLE"));
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void T24_HTTP_200_이어도_resultCode_E401_이면_UNAUTHORIZED_이고_요청_1번() {
        server.enqueue(ok("{\"resultCode\":\"E401\",\"resultMessage\":\"UNAUTHORIZED\",\"data\":null}"));
        server.enqueue(ok(Fixtures.read("supplier-b/search.json")));

        assertThat(search()).isEqualTo(SupplierResult.failure(FailureReason.UNAUTHORIZED, "E401 UNAUTHORIZED"));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void SB05_resultCode_0000_인데_data_가_null_이면_MALFORMED() {
        server.enqueue(ok("{\"resultCode\":\"0000\",\"resultMessage\":\"SUCCESS\",\"data\":null}"));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
    }

    @Test
    void SB05_resultCode_가_없으면_MALFORMED_이고_재시도_없음() {
        server.enqueue(ok("{}"));
        server.enqueue(ok(Fixtures.read("supplier-b/search.json")));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void SB05_본문이_JSON_이_아니면_MALFORMED() {
        server.enqueue(ok("not json"));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
    }

    @Test
    void SB05_방어적으로_HTTP_503_도_UNAVAILABLE() {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.UNAVAILABLE));
    }

    @Test
    void SB_무응답이면_TIMEOUT() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        assertThat(search()).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.TIMEOUT));
    }

    @Test
    void T28_재고_요청은_API_키_헤더와_SB03_경로_쿼리로_간다() throws InterruptedException {
        server.enqueue(ok(Fixtures.read("supplier-b/search.json")));

        adapter.fetchAvailability(List.of("B77120", "B77121"), SEP_1_TO_4).block(Duration.ofSeconds(5));

        var request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getHeader("X-Api-Key")).isEqualTo("key-b");
        assertThat(request.getPath()).isEqualTo(
                "/b/api/search?propertyIds=B77120,B77121&checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0");
    }

    @Test
    void T29_목록_정상_응답이면_CatalogEntry_1건_객실_타입_1건() throws InterruptedException {
        server.enqueue(ok(Fixtures.read("supplier-b/properties.json")));

        SupplierResult<List<CatalogEntry>> result = adapter.fetchCatalog().block(Duration.ofSeconds(5));

        assertThat(server.takeRequest(1, TimeUnit.SECONDS).getPath()).isEqualTo("/b/api/properties");
        assertThat(result).isInstanceOfSatisfying(SupplierResult.Success.class, success -> {
            @SuppressWarnings("unchecked")
            List<CatalogEntry> catalog = (List<CatalogEntry>) success.value();
            assertThat(catalog).hasSize(1);
            assertThat(catalog.get(0).roomTypes()).hasSize(1);
        });
    }

    @Test
    void SB02_목록도_HTTP_200_E503_이면_UNAVAILABLE() {
        server.enqueue(ok(E503));
        server.enqueue(ok(E503));

        assertThat(adapter.fetchCatalog().block(Duration.ofSeconds(5))).isInstanceOfSatisfying(
                SupplierResult.Failure.class, failure -> assertThat(failure.reason()).isEqualTo(FailureReason.UNAVAILABLE));
    }
}
