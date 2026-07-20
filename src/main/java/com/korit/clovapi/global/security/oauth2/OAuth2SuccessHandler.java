package com.korit.clovapi.global.security.oauth2;

import com.korit.clovapi.domain.auth.oauth.OAuthOneTimeCodeStore;
import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuthOneTimeCodeStore codeStore;
    private final String redirectUrl;

    public OAuth2SuccessHandler(
            OAuthOneTimeCodeStore codeStore,
            @Value("${app.oauth2.redirect-url}") String redirectUrl
    ) {
        this.codeStore = codeStore;
        this.redirectUrl = redirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws java.io.IOException, jakarta.servlet.ServletException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        OAuthProfile profile = (OAuthProfile) user.getAttributes().get("oauthProfile");
        String code = codeStore.issue(profile);
        getRedirectStrategy().sendRedirect(
                request,
                response,
                UriComponentsBuilder.fromUriString(redirectUrl).queryParam("code", code).build().toUriString()
        );
    }
}
