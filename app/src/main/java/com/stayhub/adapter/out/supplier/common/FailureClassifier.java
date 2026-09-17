package com.stayhub.adapter.out.supplier.common;

import com.fasterxml.jackson.core.JacksonException;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierResult;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * 공급사 호출 중 난 예외를 FailureReason 으로 바꾼다.
 * WebClient 는 원인 예외를 감싸서 던지므로 원인 사슬 전체를 보고 판정한다.
 * 판정 우선순위: 타임아웃 > 연결 실패 > 파싱 실패 > HTTP 상태 코드 > 그 외.
 * netty ConnectTimeoutException 은 ConnectException 의 하위 타입이라 반드시 타임아웃을 먼저 본다.
 */
public final class FailureClassifier {

    private static final int BODY_SNIPPET_LENGTH = 200;

    private FailureClassifier() {
    }

    public static <T> SupplierResult<T> classify(Throwable error) {
        List<Throwable> chain = causeChain(error);

        Optional<Throwable> timeout = find(chain,
                TimeoutException.class, ReadTimeoutException.class, ConnectTimeoutException.class);
        if (timeout.isPresent()) {
            return SupplierResult.failure(FailureReason.TIMEOUT, describe(timeout.get()));
        }

        Optional<Throwable> connect = find(chain, ConnectException.class, UnknownHostException.class);
        if (connect.isPresent()) {
            return SupplierResult.failure(FailureReason.UNAVAILABLE, describe(connect.get()));
        }

        // 버퍼 한도 초과는 다시 요청해도 크기가 같으므로 재시도하지 않는 MALFORMED 로 본다 (FX-01)
        Optional<Throwable> decoding = find(chain,
                DecodingException.class, JacksonException.class, DataBufferLimitException.class);
        if (decoding.isPresent()) {
            return SupplierResult.failure(FailureReason.MALFORMED, describe(decoding.get()));
        }

        Optional<Throwable> response = find(chain, WebClientResponseException.class);
        if (response.isPresent()) {
            var responseError = (WebClientResponseException) response.get();
            return SupplierResult.failure(
                    reasonForStatus(responseError.getStatusCode().value()),
                    describe(responseError) + bodySnippet(responseError));
        }

        return SupplierResult.failure(FailureReason.SUPPLIER_ERROR, describe(error));
    }

    public static FailureReason reasonForStatus(int status) {
        return switch (status) {
            case 400 -> FailureReason.BAD_REQUEST;
            case 401 -> FailureReason.UNAUTHORIZED;
            case 429 -> FailureReason.RATE_LIMITED;
            case 503 -> FailureReason.UNAVAILABLE;
            default -> status >= 500 ? FailureReason.SUPPLIER_ERROR : FailureReason.BAD_REQUEST;
        };
    }

    private static List<Throwable> causeChain(Throwable error) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = error; current != null && !chain.contains(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }

    @SafeVarargs
    private static Optional<Throwable> find(List<Throwable> chain, Class<? extends Throwable>... types) {
        for (Throwable candidate : chain) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static String describe(Throwable error) {
        String name = error.getClass().getSimpleName();
        return error.getMessage() == null ? name : name + ": " + error.getMessage();
    }

    private static String bodySnippet(WebClientResponseException error) {
        String body = error.getResponseBodyAsString();
        if (body == null || body.isEmpty()) {
            return "";
        }
        return " body=" + body.substring(0, Math.min(BODY_SNIPPET_LENGTH, body.length()));
    }
}
