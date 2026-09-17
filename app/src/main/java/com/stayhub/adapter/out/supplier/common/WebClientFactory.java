package com.stayhub.adapter.out.supplier.common;

import com.stayhub.domain.SupplierCode;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * 공급사별 WebClient 를 만들고 캐시한다.
 * base URL 과 X-Api-Key 헤더, 연결 타임아웃만 건다. 응답 타임아웃은 SupplierCallExecutor 가 건다.
 */
public class WebClientFactory {

    private final SupplierProperties suppliers;
    private final Duration connectTimeout;
    private final Map<SupplierCode, WebClient> clients = new ConcurrentHashMap<>();

    public WebClientFactory(SupplierProperties suppliers, Duration connectTimeout) {
        this.suppliers = suppliers;
        this.connectTimeout = connectTimeout;
    }

    public WebClient webClient(SupplierCode supplier) {
        return clients.computeIfAbsent(supplier, this::create);
    }

    private WebClient create(SupplierCode supplier) {
        SupplierProperties.Entry entry = suppliers.entry(supplier);
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()));
        return WebClient.builder()
                .baseUrl(entry.baseUrl())
                .defaultHeader("X-Api-Key", entry.apiKey())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
