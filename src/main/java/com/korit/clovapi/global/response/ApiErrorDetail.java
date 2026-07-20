package com.korit.clovapi.global.response;

public record ApiErrorDetail(
        String field,
        String reason
) {
}
