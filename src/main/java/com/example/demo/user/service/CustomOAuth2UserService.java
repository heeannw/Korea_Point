package com.example.demo.user.service;

import com.example.demo.user.dto.CustomOAuth2User;
import com.example.demo.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // provider 이름 (google, kakao 등)
        String provider = userRequest.getClientRegistration().getRegistrationId();

        Map<String, Object> attributes = oAuth2User.getAttributes();

        OAuthProfile profile = OAuthProfile.from(provider, attributes);

        // DB에 유저 저장 또는 조회
        User user = userService.processOAuth2User(provider, profile.providerId(), profile.email(), profile.name(), profile.profileImage());

        // DB userId를 SecurityContext에 함께 보관하기 위해 CustomOAuth2User로 래핑
        return new CustomOAuth2User(oAuth2User, user.getId(), user.getRole(), user.getName(), provider);
    }

    private record OAuthProfile(String providerId, String email, String name, String profileImage) {

        static OAuthProfile from(String provider, Map<String, Object> attributes) {
            return switch (provider) {
                case "naver" -> fromNaver(attributes);
                case "kakao" -> fromKakao(attributes);
                default -> fromGoogle(attributes);
            };
        }

        private static OAuthProfile fromGoogle(Map<String, Object> attributes) {
            String providerId = value(attributes.get("sub"));
            return new OAuthProfile(
                    providerId,
                    fallbackEmail(value(attributes.get("email")), "google", providerId),
                    fallbackName(value(attributes.get("name")), "Google 사용자"),
                    value(attributes.get("picture"))
            );
        }

        @SuppressWarnings("unchecked")
        private static OAuthProfile fromNaver(Map<String, Object> attributes) {
            Map<String, Object> response = (Map<String, Object>) attributes.getOrDefault("response", Map.of());
            String providerId = value(response.get("id"));
            return new OAuthProfile(
                    providerId,
                    fallbackEmail(value(response.get("email")), "naver", providerId),
                    fallbackName(value(response.get("name")), fallbackName(value(response.get("nickname")), "네이버 사용자")),
                    value(response.get("profile_image"))
            );
        }

        @SuppressWarnings("unchecked")
        private static OAuthProfile fromKakao(Map<String, Object> attributes) {
            String providerId = value(attributes.get("id"));
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());
            return new OAuthProfile(
                    providerId,
                    fallbackEmail(value(kakaoAccount.get("email")), "kakao", providerId),
                    fallbackName(value(profile.get("nickname")), "카카오 사용자"),
                    value(profile.get("profile_image_url"))
            );
        }

        private static String value(Object value) {
            return value == null ? "" : String.valueOf(value);
        }

        private static String fallbackName(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static String fallbackEmail(String value, String provider, String providerId) {
            return value == null || value.isBlank()
                    ? providerId + "@" + provider + ".oauth.local"
                    : value;
        }
    }
}
