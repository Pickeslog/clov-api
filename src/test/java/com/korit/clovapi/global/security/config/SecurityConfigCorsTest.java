package com.korit.clovapi.global.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityConfigCorsTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void splitsCommaSeparatedOriginsIntoIndividuallyMatchedOrigins() {
        CorsConfiguration config = corsConfigFor("https://clovlabcalss.store,https://clovlov.xyz");

        assertEquals("https://clovlabcalss.store", config.checkOrigin("https://clovlabcalss.store"));
        assertEquals("https://clovlov.xyz", config.checkOrigin("https://clovlov.xyz"));
        assertNull(config.checkOrigin("https://evil.example"));
    }

    @Test
    void stillWorksWithASingleOriginAndNoComma() {
        CorsConfiguration config = corsConfigFor("https://clovlabcalss.store");

        assertEquals("https://clovlabcalss.store", config.checkOrigin("https://clovlabcalss.store"));
    }

    @Test
    void trimsWhitespaceAndDropsBlankEntriesFromATrailingComma() {
        CorsConfiguration config = corsConfigFor(" https://clovlabcalss.store , https://clovlov.xyz ,");

        assertEquals("https://clovlabcalss.store", config.checkOrigin("https://clovlabcalss.store"));
        assertEquals("https://clovlov.xyz", config.checkOrigin("https://clovlov.xyz"));
    }

    private CorsConfiguration corsConfigFor(String allowedOriginsEnvValue) {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(allowedOriginsEnvValue);
        return source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/rooms"));
    }
}
