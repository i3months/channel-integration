package com.stayhub.adapter.out.supplier.a;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.supplier.Fixtures;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SupplierAFailureMapperTest {

    private final SupplierAFailureMapper mapper = new SupplierAFailureMapper(Fixtures.OBJECT_MAPPER);

    @ParameterizedTest
    @CsvSource({
            "400, BAD_REQUEST",
            "401, UNAUTHORIZED",
            "429, RATE_LIMITED",
            "500, SUPPLIER_ERROR",
            "503, UNAVAILABLE",
            "502, SUPPLIER_ERROR"
    })
    void T10_HTTP_상태_코드로_실패_사유를_정한다(int status, FailureReason expected) {
        SupplierResult.Failure<Object> failure = mapper.toFailure(status, "{}");

        assertThat(failure.reason()).isEqualTo(expected);
    }

    @Test
    void SA05_본문에_error_필드가_있으면_상태_error_message_형태로_메시지를_만든다() {
        SupplierResult.Failure<Object> failure = mapper.toFailure(503,
                "{\"error\":\"SERVICE_UNAVAILABLE\",\"message\":\"temporarily unavailable\"}");

        assertThat(failure.message()).isEqualTo("503 SERVICE_UNAVAILABLE: temporarily unavailable");
    }

    @Test
    void SA05_본문이_JSON_이_아니거나_error_가_없으면_상태와_본문_앞부분을_남긴다() {
        assertThat(mapper.toFailure(502, "<html>Bad Gateway</html>").message())
                .isEqualTo("502 body=<html>Bad Gateway</html>");
        assertThat(mapper.toFailure(500, "").message()).isEqualTo("500");
    }
}
