package com.stayhub.adapter.out.supplier.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonParseException;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierResult;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class FailureClassifierTest {

    private static FailureReason reasonOf(Throwable error) {
        return ((SupplierResult.Failure<?>) FailureClassifier.classify(error)).reason();
    }

    private static String messageOf(Throwable error) {
        return ((SupplierResult.Failure<?>) FailureClassifier.classify(error)).message();
    }

    private static WebClientResponseException responseError(int status, String body) {
        return WebClientResponseException.create(status, "status " + status, HttpHeaders.EMPTY,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private static WebClientRequestException requestError(Throwable cause) {
        return new WebClientRequestException(cause, HttpMethod.GET, URI.create("http://localhost:9090/a/v1/hotels"),
                HttpHeaders.EMPTY);
    }

    @Test
    void AD03_타임아웃_계열은_TIMEOUT() {
        assertThat(reasonOf(new TimeoutException("late"))).isEqualTo(FailureReason.TIMEOUT);
        assertThat(reasonOf(ReadTimeoutException.INSTANCE)).isEqualTo(FailureReason.TIMEOUT);
        assertThat(reasonOf(new ConnectTimeoutException("connect timed out"))).isEqualTo(FailureReason.TIMEOUT);
    }

    @Test
    void AD03_연결_타임아웃은_ConnectException_의_하위_타입이어도_UNAVAILABLE_이_아니라_TIMEOUT() {
        assertThat(new ConnectTimeoutException("x")).isInstanceOf(ConnectException.class);

        assertThat(reasonOf(requestError(new ConnectTimeoutException("connect timed out"))))
                .isEqualTo(FailureReason.TIMEOUT);
    }

    @Test
    void AD03_연결_실패_계열은_UNAVAILABLE() {
        assertThat(reasonOf(new ConnectException("Connection refused"))).isEqualTo(FailureReason.UNAVAILABLE);
        assertThat(reasonOf(new UnknownHostException("no-such-host"))).isEqualTo(FailureReason.UNAVAILABLE);
    }

    @Test
    void AD03_WebClient_가_감싼_예외도_원인을_따라가_판정한다() {
        assertThat(reasonOf(requestError(new ConnectException("Connection refused"))))
                .isEqualTo(FailureReason.UNAVAILABLE);
    }

    @ParameterizedTest
    @CsvSource({
            "400, BAD_REQUEST",
            "401, UNAUTHORIZED",
            "429, RATE_LIMITED",
            "503, UNAVAILABLE",
            "500, SUPPLIER_ERROR",
            "502, SUPPLIER_ERROR",
            "504, SUPPLIER_ERROR",
            "404, BAD_REQUEST",
            "403, BAD_REQUEST"
    })
    void AD03_T10_HTTP_상태_코드로_판정한다(int status, FailureReason expected) {
        assertThat(reasonOf(responseError(status, "{}"))).isEqualTo(expected);
        assertThat(FailureClassifier.reasonForStatus(status)).isEqualTo(expected);
    }

    @Test
    void AD03_파싱_실패는_MALFORMED() {
        assertThat(reasonOf(new DecodingException("JSON decoding error"))).isEqualTo(FailureReason.MALFORMED);
        assertThat(reasonOf(new JsonParseException(null, "Unexpected character"))).isEqualTo(FailureReason.MALFORMED);
        assertThat(reasonOf(new DecodingException("wrap", new JsonParseException(null, "bad"))))
                .isEqualTo(FailureReason.MALFORMED);
    }

    @Test
    void AD03_그_외_예외는_SUPPLIER_ERROR() {
        assertThat(reasonOf(new IllegalStateException("boom"))).isEqualTo(FailureReason.SUPPLIER_ERROR);
    }

    @Test
    void AD03_메시지에_예외_클래스_단순_이름과_예외_메시지가_들어간다() {
        assertThat(messageOf(new IllegalStateException("boom"))).isEqualTo("IllegalStateException: boom");
    }

    @Test
    void AD03_응답_본문이_있으면_앞_200자를_덧붙인다() {
        String longBody = "x".repeat(250);

        String message = messageOf(responseError(503, longBody));

        assertThat(message).startsWith("ServiceUnavailable: ");
        assertThat(message).endsWith(" body=" + "x".repeat(200));
    }

    @Test
    void T101_버퍼_한도_초과는_다시_요청해도_같으므로_MALFORMED() {
        assertThat(reasonOf(new DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144")))
                .isEqualTo(FailureReason.MALFORMED);
    }
}
