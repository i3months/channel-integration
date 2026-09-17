package com.stayhub.domain;

/**
 * 공급사 호출 결과. 포트는 예외를 던지지 않고 성공 또는 실패 사유를 이 타입으로 돌려준다.
 */
public sealed interface SupplierResult<T> permits SupplierResult.Success, SupplierResult.Failure {

    static <T> SupplierResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> SupplierResult<T> failure(FailureReason reason, String message) {
        return new Failure<>(reason, message);
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default boolean isFailure() {
        return this instanceof Failure<T>;
    }

    record Success<T>(T value) implements SupplierResult<T> {
    }

    record Failure<T>(FailureReason reason, String message) implements SupplierResult<T> {
    }
}
