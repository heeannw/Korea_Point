package com.example.demo.user.util;

import com.example.demo.user.dto.CustomOAuth2User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtil {

    private SecurityUtil() {}

    /**
     * 현재 로그인한 유저의 DB users.id를 반환한다.
     * 로그인하지 않은 상태에서 호출하면 IllegalStateException 발생.
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User user) {
            return user.getUserId();
        }
        throw new IllegalStateException("로그인이 필요합니다.");
    }

    /** 현재 로그인 여부 확인 */
    public static boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CustomOAuth2User;
    }

    /** 현재 로그인한 유저의 표시 이름 반환 (Google "name" 속성) */
    public static String getCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User user) {
            return (String) user.getAttributes().get("name");
        }
        return null;
    }
}
