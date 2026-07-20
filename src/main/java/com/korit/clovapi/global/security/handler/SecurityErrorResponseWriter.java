package com.korit.clovapi.global.security.handler;

import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.failure(errorCode.code(), errorCode.message())
        );
    }
}
