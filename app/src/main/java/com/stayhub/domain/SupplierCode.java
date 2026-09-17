package com.stayhub.domain;

import java.util.Locale;

public enum SupplierCode {
    A,
    B;

    public static SupplierCode fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("unknown supplier: null");
        }
        try {
            return SupplierCode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown supplier: " + value, e);
        }
    }
}
