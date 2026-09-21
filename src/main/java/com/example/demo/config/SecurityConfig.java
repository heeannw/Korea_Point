package com.example.demo.config;

import com.example.demo.user.service.CustomOAuth2UserService;
import com.example.demo.user.dto.CustomOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.LocaleResolver;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final LocaleResolver localeResolver;

    /** 로그아웃 직전의 언어를 잠시 담아 두는 요청 속성 이름. */
    private static final String LOGOUT_LANGUAGE = SecurityConfig.class.getName() + ".logoutLanguage";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/community/**").authenticated()
                .requestMatchers("/api/comments/**").authenticated()
                .requestMatchers("/api/reservations/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers("/api/reports/**").authenticated()
                .requestMatchers("/api/trip/plan/**").authenticated()
                .requestMatchers("/api/trip/reservation/**").authenticated()
                .requestMatchers("/api/transport/history/**").authenticated()
                .requestMatchers("/api/kakao/**").authenticated()
                .requestMatchers("/api/points/charge/**").authenticated()
                // 아래 규칙은 "적어둔 것만 막고 나머지는 연다"(anyRequest().permitAll()) 구조라,
                // 새 화면·새 API 는 적어 주지 않으면 조용히 전부 공개된다. 챗봇과 여행/교통/관광
                // API 가 그렇게 열린 채로 있었다 — 로그인 없이 부를 수 있으니 바깥에서 스크립트로
                // 계속 때리면 Gemini·번역·관광공사 호출이 그대로 나간다.
                // (결제 웹훅 /api/portone/webhook · /api/points/webhook 은 밖에서 우리를 부르는
                //  통로라 여기 넣으면 안 된다. 그 둘은 계속 열어 둔다.)
                .requestMatchers("/chatbot/**").authenticated()
                .requestMatchers("/api/trip/**").authenticated()
                .requestMatchers("/api/transport/**").authenticated()
                .requestMatchers("/api/tourapi/**").authenticated()
                .requestMatchers("/api/translate").authenticated()
                .requestMatchers("/api/reservation-place-policies").authenticated()
                .requestMatchers("/tour/**").authenticated()
                .requestMatchers("/transport/**").authenticated()
                .requestMatchers("/foodmap/**").authenticated()
                .requestMatchers("/cafe/**").authenticated()
                // 홈은 로그인 없이 열어 둔다. 국문 홈만 공개이고 영문 홈이 막혀 있어
                // 브라우저 언어가 en 인 방문자는 /en <-> /ko 를 오가며 무한 새로고침에 걸렸다.
                // (/en/** 하위 화면은 아래 규칙대로 로그인이 필요하다)
                .requestMatchers("/ko", "/kr", "/en").permitAll()
                .requestMatchers("/en/**").authenticated()
                .requestMatchers("/ko/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    response.sendRedirect(loginSuccessUrl(request));
                })
                .failureHandler((request, response, exception) -> {
                    log.warn("OAuth2 login failed: {}", exception.getMessage(), exception);
                    response.sendRedirect(loginFailureUrl(request));
                })
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("loginId")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    response.sendRedirect(loginSuccessUrl(request));
                })
                .failureHandler((request, response, exception) -> {
                    response.sendRedirect(loginFailureUrl(request));
                })
                .permitAll()
            )
            .logout(logout -> logout
                // 로그아웃은 세션을 비운다. 언어도 세션에 들어 있어서 그대로 두면 한국어로 돌아간다.
                // 비우기 전에 읽어 두었다가 돌아갈 주소에 실어 보낸다.
                // (여기서 더한 처리는 세션을 비우는 기본 처리보다 먼저 돈다)
                .addLogoutHandler((request, response, authentication) ->
                        request.setAttribute(LOGOUT_LANGUAGE, currentLanguage(request)))
                .logoutSuccessHandler((request, response, authentication) -> {
                    Object saved = request.getAttribute(LOGOUT_LANGUAGE);
                    String lang = saved instanceof String value ? value : CurrentLanguage.KO;
                    String home = CurrentLanguage.EN.equals(lang) ? "/en" : "/ko";
                    response.sendRedirect(home + "?lang=" + lang);
                })
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"로그인이 필요합니다\"}");
                        return;
                    }
                    // 홈으로 돌려보낸다. 로그인이 필요한 메뉴를 누르는 경우는 홈 화면의
                    // openLoginWindow 가 먼저 가로채 로그인 팝업을 띄우고, 로그인하면 원래
                    // 가려던 자리로 되돌려준다(home.html 의 isProtectedHomeLink 참고).
                    // 여기까지 오는 건 주소를 직접 친 경우뿐이다.
                    response.sendRedirect("/ko");
                })
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/comments/**", "/api/reports/**",
                        "/api/portone/webhook", "/api/points/webhook",
                        "/api/trip/recommend", "/api/trip/reoptimize")
            );

        return http.build();
    }

    /**
     * 이 요청의 언어. 보안 필터는 스프링 MVC 보다 앞에서 돌아 LocaleContextHolder 가 아직
     * 세션 언어를 모른다. 세션에서 직접 읽는다.
     */
    private String currentLanguage(HttpServletRequest request) {
        return CurrentLanguage.of(localeResolver.resolveLocale(request));
    }

    private String loginSuccessUrl(HttpServletRequest request) {
        String url = "/login/success?lang=" + currentLanguage(request);
        Object principal = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        if (principal instanceof CustomOAuth2User user
                && user.getProvider() != null
                && !user.getProvider().isBlank()) {
            url += "&provider=" + user.getProvider();
        }
        return url;
    }

    private String loginFailureUrl(HttpServletRequest request) {
        return "/login?error=true&lang=" + currentLanguage(request);
    }
}
