package com.korit.clovapi.global.response;

public record ApiError(
        String code,
        String message
) {
}
