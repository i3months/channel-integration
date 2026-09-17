package com.stayhub.adapter.out.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.supplier.a.SupplierAAdapter;
import com.stayhub.adapter.out.supplier.a.SupplierAFailureMapper;
import com.stayhub.adapter.out.supplier.a.SupplierAMapper;
import com.stayhub.adapter.out.supplier.b.SupplierBAdapter;
import com.stayhub.adapter.out.supplier.b.SupplierBFailureMapper;
import com.stayhub.adapter.out.supplier.b.SupplierBMapper;
import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierCallExecutor;
import com.stayhub.adapter.out.supplier.common.SupplierProperties;
import com.stayhub.adapter.out.supplier.common.WebClientFactory;
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
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FX-02. 숙소 코드의 특수 문자가 요청을 깨뜨리거나(`{`) 다른 값으로 바뀌지(`+`) 않아야 한다.
 */
class SupplierRequestUriTest {

    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);
    private static final List<String> TRICKY_CODES = List.of("A+1", "A{1}", "a&b=c", "x y");

    private MockWebServer server;
    private SupplierAAdapter adapterA;
    private SupplierBAdapter adapterB;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        var suppliers = new SupplierProperties(Map.of(
                SupplierCode.A, new SupplierProperties.Entry(baseUrl, "key-a"),
                SupplierCode.B, new SupplierProperties.Entry(baseUrl, "key-b")));
        var retry = new IntegrationProperties.Retry(1, Duration.ofMillis(10));
        var integration = new IntegrationProperties(true, Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(2), Duration.ofSeconds(5), 50, 4, retry);
        var webClients = new WebClientFactory(suppliers, integration.connectTimeout());
        var executor = new SupplierCallExecutor(retry);
        adapterA = new SupplierAAdapter(webClients, executor, integration, new SupplierAMapper(Fixtures.OBJECT_MAPPER),
                new SupplierAFailureMapper(Fixtures.OBJECT_MAPPER), Fixtures.OBJECT_MAPPER);
        adapterB = new SupplierBAdapter(webClients, executor, integration, new SupplierBMapper(Fixtures.OBJECT_MAPPER),
                new SupplierBFailureMapper(), Fixtures.OBJECT_MAPPER);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse ok(String body) {
        return new MockResponse().addHeader("Content-Type", "application/json").setBody(body);
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        return server.takeRequest(1, TimeUnit.SECONDS);
    }

    @Test
    void T103_A_코드에_특수_문자가_있어도_예외_없이_보내고_공급사는_원래_코드를_받는다() throws InterruptedException {
        server.enqueue(ok("{\"items\":[]}"));

        var result = adapterA.fetchAvailability(TRICKY_CODES, SEP_1_TO_4).block(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(SupplierResult.success(List.of()));
        RecordedRequest request = takeRequest();
        assertThat(request.getRequestUrl().queryParameter("hotelCodes")).isEqualTo(String.join(",", TRICKY_CODES));
        assertThat(request.getRequestUrl().queryParameter("checkIn")).isEqualTo("2026-09-01");
    }

    @Test
    void T104_B_코드에_특수_문자가_있어도_예외_없이_보내고_공급사는_원래_코드를_받는다() throws InterruptedException {
        server.enqueue(ok("{\"resultCode\":\"0000\",\"resultMessage\":\"SUCCESS\",\"data\":{\"items\":[]}}"));

        var result = adapterB.fetchAvailability(TRICKY_CODES, SEP_1_TO_4).block(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(SupplierResult.success(List.of()));
        assertThat(takeRequest().getRequestUrl().queryParameter("propertyIds")).isEqualTo(String.join(",", TRICKY_CODES));
    }

    @Test
    void T105_평범한_코드는_구분자_쉼표가_그대로_나간다() throws InterruptedException {
        server.enqueue(ok("{\"items\":[]}"));
        server.enqueue(ok("{\"resultCode\":\"0000\",\"resultMessage\":\"SUCCESS\",\"data\":{\"items\":[]}}"));

        adapterA.fetchAvailability(List.of("A-10023", "A-10044"), SEP_1_TO_4).block(Duration.ofSeconds(5));
        adapterB.fetchAvailability(List.of("B77120", "B77121"), SEP_1_TO_4).block(Duration.ofSeconds(5));

        assertThat(takeRequest().getPath()).isEqualTo(
                "/a/v1/availability?hotelCodes=A-10023,A-10044&checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0");
        assertThat(takeRequest().getPath()).isEqualTo(
                "/b/api/search?propertyIds=B77120,B77121&checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0");
    }

    @Test
    void FX02_URI_조립은_구독_시점에_일어나_호출_실행기_안에서_처리된다() {
        server.enqueue(ok("{\"items\":[]}"));

        // 조립 시점에 예외가 나면 이 줄에서 바로 던져진다. 구독 전에는 아무 일도 없어야 한다.
        var mono = adapterA.fetchAvailability(List.of("A{1}"), SEP_1_TO_4);

        assertThat(server.getRequestCount()).isZero();
        assertThat(mono.block(Duration.ofSeconds(5))).isNotNull();
    }
}
