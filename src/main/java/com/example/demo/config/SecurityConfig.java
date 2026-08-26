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
                    Object language = request.getSession().getAttribute("loginLanguage");
                    response.sendRedirect("/ko");
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
                .defaultSuccessUrl("/ko", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**", "/api/comments/**", "/api/reports/**",
                        "/api/trip/recommend", "/api/trip/reoptimize")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // H2 肄섏넄 iframe ?덉슜
            );

        return http.build();
    }
}
