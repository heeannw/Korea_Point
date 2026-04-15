package com.example.demo.user.util;

import com.example.demo.user.dto.CustomOAuth2User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class SecurityUtil {

    private SecurityUtil() {}

    /**
     * ?꾩옱 濡쒓렇?명븳 ?좎???DB users.id瑜?諛섑솚?쒕떎.
     * 濡쒓렇?명븯吏 ?딆? ?곹깭?먯꽌 ?몄텧?섎㈃ IllegalStateException 諛쒖깮.
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User user) {
            return user.getUserId();
        }
        throw new IllegalStateException("濡쒓렇?몄씠 ?꾩슂?⑸땲??");
    }

    /** ?꾩옱 濡쒓렇???щ? ?뺤씤 */
    public static boolean isLoggedIn() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** ?꾩옱 濡쒓렇?명븳 ?좎????쒖떆 ?대쫫 諛섑솚 (Google "name" ?띿꽦) */
    public static String getCurrentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomOAuth2User user) {
            Object name = user.getAttributes().get("name");
            return name != null ? name.toString() : auth.getName();
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
