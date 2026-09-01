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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/community/**").authenticated()
                .requestMatchers("/api/comments/**").authenticated()
                .requestMatchers("/api/reports/**").authenticated()
                .requestMatchers("/api/trip/plan/**").authenticated()
                .requestMatchers("/api/trip/reservation/**").authenticated()
                .requestMatchers("/api/transport/history/**").authenticated()
                .requestMatchers("/api/kakao/**").authenticated()
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
                    response.sendRedirect("/login?error=true");
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
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"로그인이 필요합니다\"}");
                        return;
                    }
                    response.sendRedirect("/ko");
                })
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/comments/**", "/api/reports/**",
                        "/api/trip/recommend", "/api/trip/reoptimize")
            );

        return http.build();
    }

    private String loginSuccessUrl(HttpServletRequest request) {
        Object language = request.getSession().getAttribute("loginLanguage");
        String selected = language instanceof String value ? value : "ko";
        String url = "/login/success?lang=" + selected;
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
}
