package com.korit.clovapi.global.security.config;

import com.korit.clovapi.global.security.filter.JwtAuthenticationFilter;
import com.korit.clovapi.global.security.handler.JwtAccessDeniedHandler;
import com.korit.clovapi.global.security.handler.JwtAuthenticationEntryPoint;
import com.korit.clovapi.global.security.jwt.JwtProperties;
import com.korit.clovapi.global.security.oauth2.CustomOAuth2UserService;
import com.korit.clovapi.global.security.oauth2.LoginPromptAuthorizationRequestResolver;
import com.korit.clovapi.global.security.oauth2.OAuth2FailureHandler;
import com.korit.clovapi.global.security.oauth2.OAuth2SuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2SuccessHandler oauth2SuccessHandler,
            OAuth2FailureHandler oauth2FailureHandler,
            CorsConfigurationSource corsConfigurationSource,
            LoginPromptAuthorizationRequestResolver loginPromptAuthorizationRequestResolver
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        // /api/actuator/** — 배포 헬스체크(#108). 인증 없는 폴링 루프가 확인해야 하므로 열어둔다.
                        .requestMatchers("/api/v1/auth/**", "/oauth2/**", "/login/**", "/swagger-ui/**",
                                "/v3/api-docs/**", "/api/actuator/**")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        // 로그아웃 후 재로그인 시 재인증을 강제한다(prompt=login) — #165.
                        .authorizationEndpoint(endpoint ->
                                endpoint.authorizationRequestResolver(loginPromptAuthorizationRequestResolver))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oauth2SuccessHandler)
                        .failureHandler(oauth2FailureHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins
    ) {
        // 플레인 @Value 플레이스홀더는 @ConfigurationProperties와 달리 콤마 구분 문자열을
        // List<String>으로 자동으로 안 쪼갠다 — 여기서 직접 split한다(#149).
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
