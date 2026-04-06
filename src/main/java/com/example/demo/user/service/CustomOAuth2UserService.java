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

        String providerId    = String.valueOf(attributes.get("sub"));   // Google 고유 ID
        String email         = (String) attributes.get("email");
        String name          = (String) attributes.get("name");
        String profileImage  = (String) attributes.get("picture");

        // DB에 유저 저장 또는 조회
        User user = userService.processOAuth2User(provider, providerId, email, name, profileImage);

        // DB userId를 SecurityContext에 함께 보관하기 위해 CustomOAuth2User로 래핑
        return new CustomOAuth2User(oAuth2User, user.getId());
    }
}
