package com.stayhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierProperties;
import com.stayhub.domain.SupplierCode;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SkeletonTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    IntegrationProperties integration;

    @Autowired
    SupplierProperties suppliers;

    @Test
    void SK_actuator_health_가_200() {
        var response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void SK_06_07_integration_설정이_기본값으로_바인딩된다() {
        assertThat(integration.syncOnStartup()).isTrue();
        assertThat(integration.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(integration.availabilityResponseTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(integration.catalogResponseTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(integration.searchBudget()).isEqualTo(Duration.ofSeconds(5));
        assertThat(integration.chunkSize()).isEqualTo(50);
        assertThat(integration.chunkConcurrency()).isEqualTo(4);
        assertThat(integration.retry().maxAttempts()).isEqualTo(2);
        assertThat(integration.retry().waitDuration()).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    void SK_06_07_suppliers_설정이_공급사_코드별로_바인딩된다() {
        assertThat(suppliers.entry(SupplierCode.A).baseUrl()).isEqualTo("http://localhost:9090");
        assertThat(suppliers.entry(SupplierCode.A).apiKey()).isEqualTo("mock-key-a");
        assertThat(suppliers.entry(SupplierCode.B).apiKey()).isEqualTo("mock-key-b");
    }
}
