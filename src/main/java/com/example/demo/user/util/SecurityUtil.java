package com.example.demo.user.util;

import com.example.demo.user.dto.CustomOAuth2User;
import com.example.demo.user.dto.LocalUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User user) {
            return user.getUserId();
        }
        if (auth != null && auth.getPrincipal() instanceof LocalUserDetails user) {
            return user.getUserId();
        }
        throw new IllegalStateException("로그인한 사용자만 이용할 수 있습니다.");
    }

    public static boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    public static String getCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User user) {
            Object name = user.getAttributes().get("name");
            return name != null ? name.toString() : auth.getName();
        }
        if (auth != null && auth.getPrincipal() instanceof LocalUserDetails user) {
            return user.getDisplayName();
        }
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oAuth2User) {
            Object name = oAuth2User.getAttributes().get("name");
            return name != null ? name.toString() : auth.getName();
        }
        if (isLoggedIn()) {
            return auth.getName();
        }
        return null;
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
