package com.korit.clovapi.global.security.oauth2;

import com.korit.clovapi.domain.auth.oauth.OAuthProfile;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        OAuthProfile profile = profile(provider, oauth2User.getAttributes());

        Map<String, Object> attributes = new HashMap<>(oauth2User.getAttributes());
        attributes.put("oauthProfile", profile);
        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        return new DefaultOAuth2User(oauth2User.getAuthorities(), attributes, userNameAttributeName);
    }

    private OAuthProfile profile(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "kakao" -> kakao(attributes);
            case "naver" -> naver(attributes);
            case "google" -> google(attributes);
            default -> throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_provider"));
        };
    }

    private OAuthProfile kakao(Map<String, Object> attributes) {
        Map<String, Object> account = map(attributes.get("kakao_account"));
        Map<String, Object> profile = map(account.get("profile"));
        return required("kakao", string(attributes.get("id")), string(account.get("email")), string(profile.get("nickname")));
    }

    private OAuthProfile naver(Map<String, Object> attributes) {
        Map<String, Object> response = map(attributes.get("response"));
        return required("naver", string(response.get("id")), string(response.get("email")), string(response.get("nickname")));
    }

    private OAuthProfile google(Map<String, Object> attributes) {
        return required("google", string(attributes.get("sub")), string(attributes.get("email")), string(attributes.get("name")));
    }

    private OAuthProfile required(String provider, String subject, String email, String nickname) {
        if (subject == null || email == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("oauth_email_required"));
        }
        String resolvedNickname = nickname == null || nickname.isBlank() ? email.substring(0, email.indexOf('@')) : nickname;
        return new OAuthProfile(provider, subject, email, resolvedNickname);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
