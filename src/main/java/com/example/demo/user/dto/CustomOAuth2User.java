package com.example.demo.user.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Google OAuth2 濡쒓렇????SecurityContext????λ릺???ъ슜??媛앹껜.
 * Google ?띿꽦(?대쫫, ?대찓???? + DB??users.id瑜??④퍡 蹂닿??쒕떎.
 */
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User delegate;  // Google?먯꽌 諛쏆? ?먮낯 OAuth2User
    private final Long userId;          // DB????λ맂 users.id
    private final String role;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomOAuth2User(OAuth2User delegate, Long userId, String role) {
        this.delegate = delegate;
        this.userId = userId;
        this.role = role;

        Collection<GrantedAuthority> mappedAuthorities = new ArrayList<>(delegate.getAuthorities());
        if (role != null && !role.isBlank()) {
            mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        }

        this.authorities = mappedAuthorities.stream()
                .collect(Collectors.toMap(GrantedAuthority::getAuthority, authority -> authority, (left, right) -> left))
                .values();
    }

    /** DB users.id */
    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** Google OAuth2??name attribute key??"sub" */
    @Override
    public String getName() {
        return delegate.getName();
    }
}
