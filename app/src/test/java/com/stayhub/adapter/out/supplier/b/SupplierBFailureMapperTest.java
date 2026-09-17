package com.stayhub.adapter.out.supplier.b;

import static org.assertj.core.api.Assertions.assertThat;

import com.stayhub.adapter.out.supplier.Fixtures;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SupplierBFailureMapperTest {

    private final SupplierBFailureMapper mapper = new SupplierBFailureMapper();

    private static com.fasterxml.jackson.databind.JsonNode body(String json) throws Exception {
        return Fixtures.OBJECT_MAPPER.readTree(json);
    }

    @ParameterizedTest
    @CsvSource({
            "E400, BAD_REQUEST",
            "E401, UNAUTHORIZED",
            "E429, RATE_LIMITED",
            "E500, SUPPLIER_ERROR",
            "E503, UNAVAILABLE",
            "E999, SUPPLIER_ERROR"
    })
    void T09_resultCode_로_실패_사유를_정한다(String resultCode, FailureReason expected) throws Exception {
        var root = body("{\"resultCode\":\"" + resultCode + "\",\"resultMessage\":\"FAIL\",\"data\":null}");

        SupplierResult.Failure<Object> failure = mapper.fromResultCode(root);

        assertThat(failure.reason()).isEqualTo(expected);
        assertThat(failure.message()).isEqualTo(resultCode + " FAIL");
    }

    @Test
    void T09_resultCode_필드가_없으면_MALFORMED() throws Exception {
        SupplierResult.Failure<Object> failure = mapper.fromResultCode(body("{\"data\":null}"));

        assertThat(failure.reason()).isEqualTo(FailureReason.MALFORMED);
    }

    @Test
    void SB05_HTTP_200_이_아니면_공통_상태_코드_규칙을_쓴다() {
        assertThat(mapper.<Object>fromHttpStatus(503, "").reason()).isEqualTo(FailureReason.UNAVAILABLE);
        assertThat(mapper.<Object>fromHttpStatus(401, "").reason()).isEqualTo(FailureReason.UNAUTHORIZED);
        assertThat(mapper.<Object>fromHttpStatus(502, "bad gateway").message()).isEqualTo("502 body=bad gateway");
    }
}
