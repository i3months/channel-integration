package com.stayhub.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SupplierCodeTest {

    @Test
    void DM01_대소문자를_무시하고_변환한다() {
        assertThat(SupplierCode.fromString("a")).isEqualTo(SupplierCode.A);
        assertThat(SupplierCode.fromString("B")).isEqualTo(SupplierCode.B);
    }

    @Test
    void DM01_없는_값이면_IllegalArgumentException() {
        assertThatThrownBy(() -> SupplierCode.fromString("x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SupplierCode.fromString(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
