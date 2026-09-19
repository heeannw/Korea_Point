package com.example.demo.notification;

import com.example.demo.config.SecurityConfig;
import com.example.demo.user.dto.CustomOAuth2User;
import com.example.demo.user.service.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = com.example.demo.community.config.CommunityUploadConfig.class))
@Import(SecurityConfig.class)
class NotificationSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean NotificationService notifications;
    @MockBean CustomOAuth2UserService oauth;

    private org.springframework.test.web.servlet.request.RequestPostProcessor member() {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var delegate = new org.springframework.security.oauth2.core.user.DefaultOAuth2User(authorities, Map.of("sub", "test"), "sub");
        var principal = new CustomOAuth2User(delegate, 7L, "USER");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    @Test
    void guestsCannotReadInboxOrCount() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test
    void mutationsRequireCsrfAndUseAuthenticatedUser() throws Exception {
        mvc.perform(patch("/api/notifications/12/read").with(member())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/notifications/read-all").with(member())).andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
        mvc.perform(patch("/api/notifications/12/read").with(member()).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(patch("/api/notifications/read-all").with(member()).with(csrf())).andExpect(status().isNoContent());
        verify(notifications).markRead(7L, 12L);
        verify(notifications).markAllRead(7L);
    }

    @Test
    void suppliedUserIdCannotSelectSomeoneElsesInbox() throws Exception {
        when(notifications.list(7L, null)).thenReturn(new NotificationService.Inbox(List.of(), 0, null));
        mvc.perform(get("/api/notifications").param("userId", "99").with(member()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        verify(notifications).list(7L, null);
    }
}
