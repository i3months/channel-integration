package com.stayhub.adapter.in.web;

/**
 * 오류 응답 본문. { "code": "...", "message": "..." }
 */
public record ApiError(String code, String message) {
}
