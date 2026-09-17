package com.stayhub.adapter.in.web;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException e) {
        return badRequest("MISSING_PARAMETER", e.getParameterName() + " is required");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> type = e.getRequiredType();
        String expected = type == LocalDate.class ? "yyyy-MM-dd"
                : type == int.class || type == Integer.class ? "an integer"
                : "valid";
        return badRequest("INVALID_PARAMETER", e.getName() + " must be " + expected);
    }

    @ExceptionHandler(InvalidSearchRequestException.class)
    public ResponseEntity<ApiError> invalidRequest(InvalidSearchRequestException e) {
        return badRequest(e.code(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        // 405, 404 같은 스프링 표준 오류는 원래 상태 코드를 유지한다
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return ResponseEntity.status(status).body(new ApiError(status.name(), status.getReasonPhrase()));
        }
        log.error("event=unexpected_error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "unexpected error"));
    }

    private static ResponseEntity<ApiError> badRequest(String code, String message) {
        return ResponseEntity.badRequest().body(new ApiError(code, message));
    }
}
