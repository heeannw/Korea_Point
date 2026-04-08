package com.example.demo.config;

import com.example.demo.user.service.CustomOAuth2UserService;
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
                // 게시글 작성·삭제, 댓글 API만 로그인 필요
                .requestMatchers("/community/**").authenticated()
                .requestMatchers("/api/comments/**").authenticated()
                .requestMatchers("/tour/**").authenticated()
                .requestMatchers("/transport/**").authenticated()
                // 그 외 모든 요청은 허용 (다른 도메인에 영향 없음)
                .anyRequest().permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/")
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
            )
            // H2 콘솔 접근을 위한 설정
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**", "/api/**")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // H2 콘솔 iframe 허용
            );

        return http.build();
    }
}
