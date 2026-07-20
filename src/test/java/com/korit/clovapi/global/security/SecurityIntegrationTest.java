package com.korit.clovapi.global.security;

import com.korit.clovapi.global.response.ApiResponse;
import com.korit.clovapi.global.security.jwt.JwtTokenProvider;
import com.korit.clovapi.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityIntegrationTest.ProtectedEndpoint.class)
class SecurityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void returnsEnvelopeWhenProtectedEndpointHasNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.details").doesNotExist());
    }

    @Test
    void acceptsAnAccessTokenWithoutAddingRoles() throws Exception {
        mockMvc.perform(get("/api/v1/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenProvider.createAccessToken(42L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @RestController
    static class ProtectedEndpoint {

        @GetMapping("/api/v1/test/protected")
        ApiResponse<Void> protectedEndpoint() {
            return ApiResponse.success(null);
        }
    }
}
