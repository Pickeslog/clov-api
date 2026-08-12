package com.korit.clovapi.global.security.filter;

import com.korit.clovapi.domain.auth.mapper.UserMapper;
import com.korit.clovapi.global.security.handler.JwtAuthenticationEntryPoint;
import com.korit.clovapi.global.security.jwt.JwtClaims;
import com.korit.clovapi.global.security.jwt.JwtTokenProvider;
import com.korit.clovapi.global.security.jwt.TokenType;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            UserMapper userMapper
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.userMapper = userMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtClaims claims = jwtTokenProvider.parse(authorization.substring(BEARER_PREFIX.length()));
            if (claims.tokenType() != TokenType.ACCESS) {
                throw new JwtException("Refresh token cannot authenticate a request");
            }
            // #159 — 탈퇴(익명화) 직후에도 이미 발급된 액세스 토큰이 TTL(30분) 동안 다른 모든
            // API에 그대로 통했다. stateless 인증에 PK 조회 한 번을 더해 막는다 — users.id가
            // PK라 비용은 무시할 만하고, is_anonymized 필터가 있는 UserService.findUser()를
            // 안 거치는 room/shop/letter 등 나머지 모든 컨트롤러에도 동일하게 적용된다.
            if (userMapper.isAnonymized(claims.userId())) {
                throw new JwtException("Anonymized account cannot authenticate a request");
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims.userId(),
                    null,
                    List.of()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("Invalid access token", exception)
            );
        }
    }
}
