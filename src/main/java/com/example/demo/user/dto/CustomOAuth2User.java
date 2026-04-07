package com.example.demo.user.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * Google OAuth2 로그인 후 SecurityContext에 저장되는 사용자 객체.
 * Google 속성(이름, 이메일 등) + DB의 users.id를 함께 보관한다.
 */
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User delegate;  // Google에서 받은 원본 OAuth2User
    private final Long userId;          // DB에 저장된 users.id

    public CustomOAuth2User(OAuth2User delegate, Long userId) {
        this.delegate = delegate;
        this.userId = userId;
    }

    /** DB users.id */
    public Long getUserId() {
        return userId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    /** Google OAuth2의 name attribute key는 "sub" */
    @Override
    public String getName() {
        return delegate.getName();
    }
}
