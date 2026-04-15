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
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 寃뚯떆湲 ?묒꽦쨌??젣, ?볤? API留?濡쒓렇???꾩슂
                .requestMatchers("/community/**").authenticated()
                .requestMatchers("/api/comments/**").authenticated()
                .requestMatchers("/api/reports/**").authenticated()
                .requestMatchers("/tour/**").authenticated()
                .requestMatchers("/transport/**").authenticated()
                .requestMatchers("/foodmap/**").authenticated()
                .requestMatchers("/cafe/**").authenticated()
                .requestMatchers("/en/**").authenticated()
                .requestMatchers("/ko/**").authenticated()
                // 洹???紐⑤뱺 ?붿껌? ?덉슜 (?ㅻⅨ ?꾨찓?몄뿉 ?곹뼢 ?놁쓬)
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
            // H2 肄섏넄 ?묎렐???꾪븳 ?ㅼ젙
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**", "/api/comments/**", "/api/reports/**")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // H2 肄섏넄 iframe ?덉슜
            );

        return http.build();
    }
}
