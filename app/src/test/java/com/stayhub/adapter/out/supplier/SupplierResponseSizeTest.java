package com.stayhub.adapter.out.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.supplier.a.SupplierAAdapter;
import com.stayhub.adapter.out.supplier.a.SupplierAFailureMapper;
import com.stayhub.adapter.out.supplier.a.SupplierAMapper;
import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierCallExecutor;
import com.stayhub.adapter.out.supplier.common.SupplierProperties;
import com.stayhub.adapter.out.supplier.common.WebClientFactory;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FX-01. WebClient 기본 버퍼 한도(256KB) 때문에 정상인 큰 응답이 실패하던 문제.
 */
class SupplierResponseSizeTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 9, 1);
    private static final StaySearchCriteria THIRTY_NIGHTS = new StaySearchCriteria(CHECK_IN, CHECK_IN.plusDays(30), 2, 0);

    private MockWebServer server;
    private SupplierAAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var suppliers = new SupplierProperties(Map.of(
                SupplierCode.A, new SupplierProperties.Entry(server.url("/").toString(), "key-a")));
        var retry = new IntegrationProperties.Retry(2, Duration.ofMillis(10));
        var integration = new IntegrationProperties(true, Duration.ofSeconds(1), Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(5), 50, 4, retry);
        adapter = new SupplierAAdapter(new WebClientFactory(suppliers, integration.connectTimeout()),
                new SupplierCallExecutor(retry), integration, new SupplierAMapper(Fixtures.OBJECT_MAPPER),
                new SupplierAFailureMapper(Fixtures.OBJECT_MAPPER), Fixtures.OBJECT_MAPPER);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /** 숙소 50개 × 객실 타입 3개 × 30박. 실제로 받을 수 있는 크기의 정상 응답. */
    private static String thirtyNightsResponse() {
        String dailyRates = IntStream.range(0, 30)
                .mapToObj(day -> "{\"date\":\"%s\",\"remainingRooms\":5,\"nightlyRate\":120000,\"taxAmount\":12000}"
                        .formatted(CHECK_IN.plusDays(day)))
                .collect(Collectors.joining(","));
        String items = IntStream.range(0, 50).boxed()
                .flatMap(hotel -> IntStream.range(0, 3).mapToObj(room -> """
                        {"hotelCode":"A-%05d","hotelName":"Hotel %d","roomTypeCode":"ROOM-%d","roomTypeName":"Room %d",
                         "maxOccupancy":2,"breakfastIncluded":false,"currency":"KRW","dailyRates":[%s]}"""
                        .formatted(hotel, hotel, room, room, dailyRates)))
                .collect(Collectors.joining(","));
        return "{\"items\":[" + items + "]}";
    }

    private static MockResponse json(String body) {
        return new MockResponse().addHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void T100_256KB_를_넘는_정상_재고_응답도_성공한다() {
        String body = thirtyNightsResponse();
        assertThat(body.length()).isGreaterThan(300_000);
        server.enqueue(json(body));
        server.enqueue(json(body));

        SupplierResult<List<Offer>> result = adapter.fetchAvailability(List.of("A-00000"), THIRTY_NIGHTS)
                .block(Duration.ofSeconds(10));

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Success.class,
                success -> assertThat((List<?>) success.value()).hasSize(150));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void T102_버퍼_한도를_넘는_응답은_MALFORMED_이고_재시도하지_않는다() {
        String oversized = "{\"items\":[], \"padding\":\"" + "x".repeat(17 * 1024 * 1024) + "\"}";
        server.enqueue(json(oversized));
        server.enqueue(json(oversized));

        SupplierResult<List<Offer>> result = adapter.fetchAvailability(List.of("A-00000"), THIRTY_NIGHTS)
                .block(Duration.ofSeconds(20));

        assertThat(result).isInstanceOfSatisfying(SupplierResult.Failure.class,
                failure -> assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED));
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
