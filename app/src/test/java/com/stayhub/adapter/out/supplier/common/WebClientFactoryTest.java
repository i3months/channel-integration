package com.stayhub.adapter.out.supplier.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;
import com.stayhub.domain.SupplierResult;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebClientFactoryTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private WebClientFactory factoryFor(String baseUrl) {
        var suppliers = new SupplierProperties(Map.of(
                SupplierCode.A, new SupplierProperties.Entry(baseUrl, "key-a"),
                SupplierCode.B, new SupplierProperties.Entry(baseUrl, "key-b")));
        return new WebClientFactory(suppliers, Duration.ofSeconds(1));
    }

    @Test
    void AD01_base_URL_로_요청하고_모든_요청에_공급사_API_키_헤더를_붙인다() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse().setBody("{}").addHeader("Content-Type", "application/json"));
        var factory = factoryFor(server.url("/").toString());

        factory.webClient(SupplierCode.A).get().uri("/a/v1/hotels").retrieve().bodyToMono(String.class)
                .block(Duration.ofSeconds(5));
        factory.webClient(SupplierCode.B).get().uri("/b/api/properties").retrieve().bodyToMono(String.class)
                .block(Duration.ofSeconds(5));

        var first = server.takeRequest(1, TimeUnit.SECONDS);
        var second = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(first.getPath()).isEqualTo("/a/v1/hotels");
        assertThat(first.getHeader("X-Api-Key")).isEqualTo("key-a");
        assertThat(second.getPath()).isEqualTo("/b/api/properties");
        assertThat(second.getHeader("X-Api-Key")).isEqualTo("key-b");
    }

    @Test
    void AD01_공급사마다_WebClient_하나를_재사용한다() {
        var factory = factoryFor(server.url("/").toString());

        assertThat(factory.webClient(SupplierCode.A)).isSameAs(factory.webClient(SupplierCode.A));
        assertThat(factory.webClient(SupplierCode.A)).isNotSameAs(factory.webClient(SupplierCode.B));
    }

    @Test
    void AD03_실제_WebClient_로_닫힌_포트에_연결하면_UNAVAILABLE_실패가_된다() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        var factory = factoryFor("http://localhost:" + closedPort);
        var executor = new SupplierCallExecutor(new IntegrationProperties.Retry(1, Duration.ofMillis(10)));

        var call = factory.webClient(SupplierCode.A).get().uri("/a/v1/hotels").retrieve().bodyToMono(String.class)
                .map(SupplierResult::success);
        var result = executor.execute(SupplierCode.A, "catalog", Duration.ofSeconds(2), call)
                .block(Duration.ofSeconds(5));

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.UNAVAILABLE));
    }

    @Test
    void AD03_실제_WebClient_로_받은_503_은_UNAVAILABLE_실패가_된다() {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"temporarily unavailable\"}"));
        var factory = factoryFor(server.url("/").toString());
        var executor = new SupplierCallExecutor(new IntegrationProperties.Retry(1, Duration.ofMillis(10)));

        var call = factory.webClient(SupplierCode.A).get().uri("/a/v1/availability").retrieve()
                .bodyToMono(String.class).map(SupplierResult::success);
        var result = executor.execute(SupplierCode.A, "availability", Duration.ofSeconds(2), call)
                .block(Duration.ofSeconds(5));

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class, failure -> {
            assertThat(failure.reason()).isEqualTo(FailureReason.UNAVAILABLE);
            assertThat(failure.message()).contains("SERVICE_UNAVAILABLE");
        });
    }
}
