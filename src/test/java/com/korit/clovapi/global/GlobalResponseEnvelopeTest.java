package com.korit.clovapi.global;

import com.korit.clovapi.global.exception.DomainException;
import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.exception.GlobalExceptionHandler;
import com.korit.clovapi.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalResponseEnvelopeTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void wrapsSuccessfulResponsesInTheContractEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("42"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void wrapsDomainExceptionsInTheContractEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test/domain-error"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.code").value("ROOM_MEMBER_NOT_FOUND"));
    }

    @Test
    void wrapsValidationFailuresInTheContractEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("입력값을 확인해주세요."))
                .andExpect(jsonPath("$.error.details.length()").value(2))
                .andExpect(jsonPath("$.error.details[*].field")
                        .value(containsInAnyOrder("email", "nickname")));
    }

    @Test
    void wrapsUnexpectedExceptionsInTheContractEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void definesTheRequiredGenericErrorCodes() {
        assertAll(
                () -> assertEquals("VALIDATION_FAILED", ErrorCode.VALIDATION_FAILED.code()),
                () -> assertEquals(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.httpStatus()),
                () -> assertEquals("UNAUTHORIZED", ErrorCode.UNAUTHORIZED.code()),
                () -> assertEquals(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.httpStatus()),
                () -> assertEquals("FORBIDDEN", ErrorCode.FORBIDDEN.code()),
                () -> assertEquals(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.httpStatus()),
                () -> assertEquals("NOT_FOUND", ErrorCode.NOT_FOUND.code()),
                () -> assertEquals(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND.httpStatus()),
                () -> assertEquals("METHOD_NOT_ALLOWED", ErrorCode.METHOD_NOT_ALLOWED.code()),
                () -> assertEquals(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.httpStatus()),
                () -> assertEquals("INTERNAL_ERROR", ErrorCode.INTERNAL_ERROR.code()),
                () -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.httpStatus())
        );
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class TestController {

        @GetMapping("/success")
        ApiResponse<Map<String, String>> success() {
            return ApiResponse.success(Map.of("id", "42"));
        }

        @GetMapping("/domain-error")
        ApiResponse<Void> domainError() {
            throw new DomainException(ErrorCode.ROOM_MEMBER_NOT_FOUND);
        }

        @GetMapping("/unexpected-error")
        ApiResponse<Void> unexpectedError() {
            throw new IllegalStateException("unexpected");
        }

        @PostMapping("/validation")
        ApiResponse<Void> validation(@Valid @RequestBody ValidationRequest request) {
            return ApiResponse.success(null);
        }
    }

    record ValidationRequest(
            @Email(message = "형식 오류") String email,
            @NotBlank(message = "필수 입력") String nickname
    ) {
    }
}
