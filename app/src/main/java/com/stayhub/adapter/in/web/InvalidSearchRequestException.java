package com.stayhub.adapter.in.web;

/**
 * 검색 요청 검증 실패. code 와 message 가 그대로 400 응답 본문이 된다.
 */
public class InvalidSearchRequestException extends RuntimeException {

    private final String code;

    public InvalidSearchRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
