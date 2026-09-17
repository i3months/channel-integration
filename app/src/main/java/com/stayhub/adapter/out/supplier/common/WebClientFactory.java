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
 * base URL 과 X-Api-Key 헤더, 연결 타임아웃, 응답 버퍼 한도를 건다. 응답 타임아웃은 SupplierCallExecutor 가 건다.
 */
public class WebClientFactory {

    /**
     * 응답 본문 버퍼 한도. WebClient 기본값 256KB 는 30박 × 50개 숙소 응답(수백 KB)이나
     * 수천 개 숙소 카탈로그(수 MB)를 담지 못한다. 메모리 보호용 상한으로 16MB 를 둔다 (FX-01).
     */
    static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

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
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .build();
    }
}
