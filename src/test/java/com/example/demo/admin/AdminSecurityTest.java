package com.example.demo.admin;

import com.example.demo.admin.controller.AdminApiController;
import com.example.demo.admin.controller.AdminPageController;
import com.example.demo.admin.dto.AdminDto;
import com.example.demo.admin.service.AdminService;
import com.example.demo.config.SecurityConfig;
import com.example.demo.user.dto.CustomOAuth2User;
import com.example.demo.user.service.CustomOAuth2UserService;
import com.example.demo.user.service.OwnerRequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminPageController.class, AdminApiController.class})
@Import(SecurityConfig.class)
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private OwnerRequestService ownerRequestService;

    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;

    @Test
    void unauthenticatedUser_isRedirectedFromAdminPage() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void regularUser_cannotAccessAdminPagesAndApi() throws Exception {
        mockMvc.perform(get("/admin/logs").with(authentication("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/api/dashboard").with(authentication("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/api/post-categories").with(authentication("USER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/api/logs").with(authentication("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUser_canAccessAdminPageAndApi() throws Exception {
        when(adminService.getDashboard()).thenReturn(AdminDto.DashboardResponse.builder()
                .summary(AdminDto.DashboardSummary.builder().build())
                .recentUsers(List.of())
                .recentPosts(List.of())
                .recentComments(List.of())
                .recentLogs(List.of())
                .build());
        when(adminService.getLogs(0, 10, null, null, null, null, null, null))
                .thenReturn(AdminDto.AdminLogPageResponse.builder()
                        .items(List.of())
                        .page(0)
                        .size(10)
                        .totalElements(0)
                        .totalPages(0)
                        .build());
        when(adminService.getPostCategories()).thenReturn(List.of());

        mockMvc.perform(get("/admin").with(authentication("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/categories").with(authentication("ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        mockMvc.perform(get("/admin/logs").with(authentication("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/api/dashboard").with(authentication("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/api/post-categories").with(authentication("ADMIN")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/api/logs").with(authentication("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminMutation_requiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/api/posts/1/hide")
                        .with(authentication("ADMIN"))
                        .contentType("application/json")
                        .content("{\"reasonCode\":\"SPAM\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRolePayload_returnsBadRequest() throws Exception {
        mockMvc.perform(patch("/admin/api/users/1/role")
                        .with(authentication("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private RequestPostProcessor authentication(String role) {
        DefaultOAuth2User delegate = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                Map.of("sub", "oauth-" + role.toLowerCase(), "name", role + " User"),
                "sub"
        );
        CustomOAuth2User principal = new CustomOAuth2User(delegate, 1L, role);
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "N/A", principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
