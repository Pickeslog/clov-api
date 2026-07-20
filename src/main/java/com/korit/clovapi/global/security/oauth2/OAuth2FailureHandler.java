package com.korit.clovapi.global.security.oauth2;

import com.korit.clovapi.global.exception.ErrorCode;
import com.korit.clovapi.global.security.handler.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SecurityErrorResponseWriter errorResponseWriter;

    public OAuth2FailureHandler(SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws java.io.IOException, jakarta.servlet.ServletException {
        errorResponseWriter.write(response, ErrorCode.OAUTH_EMAIL_REQUIRED);
    }
}
