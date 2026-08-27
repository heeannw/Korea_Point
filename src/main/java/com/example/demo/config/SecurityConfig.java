package com.example.demo.config;

import com.example.demo.user.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
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
                .requestMatchers("/ko", "/kr").permitAll()
                .requestMatchers("/en/**").authenticated()
                .requestMatchers("/ko/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .successHandler((request, response, authentication) -> {
                    response.sendRedirect(loginSuccessUrl(request));
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
        return "/login/success?lang=" + selected;
    }
}
