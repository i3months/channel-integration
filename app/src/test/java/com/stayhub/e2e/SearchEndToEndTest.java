package com.stayhub.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.mocksupplier.MockSupplierApplication;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * 실제 HTTP 로 Mock 공급사와 앱을 함께 띄워 핵심 흐름 전체를 검증한다.
 * 기동 시 동기화로 매핑을 만들고, 검색이 두 공급사를 병렬 조회해 정규화·병합한다.
 *
 * Mock 은 같은 JVM 에서 랜덤 포트로 띄운다. 앱의 application.yml 과 DB 설정을 읽지 않도록 설정 파일 이름을 바꾸고
 * DB 자동 구성을 끈다. 무응답 모드의 스레드가 종료를 붙잡지 않도록 즉시 종료로 둔다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(30)
class SearchEndToEndTest {

    private static final String SEARCH = "/api/v1/stays/search?checkIn=2026-09-01&checkOut=2026-09-04&adults=2&children=0";
    private static final Duration SEARCH_BUDGET = Duration.ofSeconds(2);

    private static ConfigurableApplicationContext mockSupplier;
    private static RestClient mockControl;

    @Autowired
    TestRestTemplate rest;

    @DynamicPropertySource
    static void supplierProperties(DynamicPropertyRegistry registry) {
        mockSupplier = new SpringApplicationBuilder(MockSupplierApplication.class)
                .properties(
                        "server.port=0",
                        "server.shutdown=immediate",
                        "spring.config.name=mock-supplier-e2e",
                        "spring.jmx.enabled=false",
                        "spring.autoconfigure.exclude="
                                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration")
                .run();
        String baseUrl = "http://localhost:" + mockSupplier.getEnvironment().getProperty("local.server.port");
        mockControl = RestClient.create(baseUrl);

        registry.add("suppliers.a.base-url", () -> baseUrl);
        registry.add("suppliers.b.base-url", () -> baseUrl);
        registry.add("integration.search-budget", SEARCH_BUDGET::toString);
        registry.add("integration.retry.wait", () -> "50ms");
    }

    @AfterAll
    static void stopMock() {
        if (mockSupplier != null) {
            mockSupplier.close();
        }
    }

    @BeforeEach
    void resetModes() {
        setMode("a", "normal");
        setMode("b", "normal");
    }

    private static void setMode(String supplier, String mode) {
        mockControl.post().uri("/control/{supplier}/mode?value={mode}", supplier, mode).retrieve().toBodilessEntity();
    }

    private JsonNode search() {
        var response = rest.getForEntity(SEARCH, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static List<String> suppliersOf(JsonNode body) {
        return body.get("items").findValuesAsText("supplier");
    }

    @Test
    void T80_정상이면_두_공급사_결과가_표준_형태로_합쳐진다() {
        JsonNode body = search();

        assertThat(body.get("failures")).isEmpty();
        assertThat(body.get("items")).hasSize(2);

        JsonNode a = body.get("items").get(0);
        assertThat(a.get("supplier").asText()).isEqualTo("A");
        assertThat(a.get("stayName").asText()).isEqualTo("Riverside Hotel Seoul");
        assertThat(a.get("roomTypeName").asText()).isEqualTo("Deluxe Twin");
        assertThat(a.get("availableRooms").asInt()).isEqualTo(1);
        assertThat(a.at("/price/totalAmount").asLong()).isEqualTo(429_000L);
        assertThat(a.at("/price/breakfastIncluded").asBoolean()).isFalse();
        assertThat(a.at("/price/daily")).hasSize(3);

        JsonNode b = body.get("items").get(1);
        assertThat(b.get("supplier").asText()).isEqualTo("B");
        assertThat(b.get("roomTypeName").asText()).isEqualTo("Deluxe Twin Room");
        assertThat(b.at("/price/totalAmount").asLong()).isEqualTo(452_000L);
        assertThat(b.at("/price/breakfastIncluded").asBoolean()).isTrue();
        assertThat(b.get("price").has("daily")).isTrue();
        assertThat(b.at("/price/daily").isNull()).isTrue();

        assertThat(a.get("stayId").asLong()).isNotEqualTo(b.get("stayId").asLong());
    }

    @Test
    void T81_A_장애면_B_결과만으로_응답하고_A_실패를_드러낸다() {
        setMode("a", "error");

        JsonNode body = search();

        assertThat(suppliersOf(body)).containsExactly("B");
        assertThat(body.get("failures")).hasSize(1);
        assertThat(body.at("/failures/0/supplier").asText()).isEqualTo("A");
        assertThat(body.at("/failures/0/reason").asText()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void B_장애는_HTTP_200_이어도_A_장애와_같은_실패로_드러난다() {
        setMode("b", "error");

        JsonNode body = search();

        assertThat(suppliersOf(body)).containsExactly("A");
        assertThat(body.at("/failures/0/supplier").asText()).isEqualTo("B");
        assertThat(body.at("/failures/0/reason").asText()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void T83_A_무응답이면_시간_예산_안에_B_결과로_응답하고_A_는_TIMEOUT() {
        setMode("a", "no-response");
        long started = System.nanoTime();

        JsonNode body = search();

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertThat(elapsedMillis).isLessThan(SEARCH_BUDGET.plusSeconds(1).toMillis());
        assertThat(suppliersOf(body)).containsExactly("B");
        assertThat(body.at("/failures/0/supplier").asText()).isEqualTo("A");
        assertThat(body.at("/failures/0/reason").asText()).isEqualTo("TIMEOUT");
    }
}
