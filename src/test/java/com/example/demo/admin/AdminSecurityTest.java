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

@WebMvcTest(controllers = {AdminPageController.class, AdminApiController.class, com.example.demo.admin.controller.AdminReservationPlaceController.class, com.example.demo.admin.controller.AdminReservationReviewController.class},
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = com.example.demo.community.config.CommunityUploadConfig.class))
@Import(SecurityConfig.class)
class AdminSecurityTest {

    @MockBean
    private com.example.demo.reservation.repository.TourReservationRepository reservations;
    @MockBean
    private com.example.demo.reservation.service.ReservationReviewService reviews;

    @Test
    void reviewingRequestsRequiresAdminCsrfAndValidInput() throws Exception {
        String url = "/admin/api/reservation-requests/1/review";
        String body = "{\"decision\":\"APPROVE\",\"amount\":17000,\"note\":\"Verified\"}";
        for (String role : List.of("USER", "OWNER")) {
            mockMvc.perform(post(url).with(authentication(role)).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/admin/api/reservation-requests").with(authentication(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(url).with(authentication("ADMIN")).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(url).with(authentication("ADMIN")).with(csrf()).contentType("application/json").content("{\"decision\":\"REJECT\",\"note\":\" \"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(reviews);
        when(reviews.review(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(com.example.demo.reservation.entity.TourReservation.builder().id(1L).build());
        mockMvc.perform(post(url).with(authentication("ADMIN")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/reservation-requests").with(authentication("ADMIN"))).andExpect(status().isOk());
        when(reviews.review(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.example.demo.reservation.service.PlaceBookingUnavailableException());
        mockMvc.perform(post(url).with(authentication("ADMIN")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @MockBean
    private com.example.demo.reservation.service.TourApiReservationService tourApiReservationService;
    @MockBean
    private com.example.demo.reservation.repository.ReservationPlacePolicyRepository placePolicyRepository;
    @MockBean
    private com.example.demo.reservation.service.ReservationPlaceAdminService placeAdminService;

    @Test
    void placePolicyWritesRequireAdminAndCsrf() throws Exception {
        String body = "{\"feeStatus\":\"PAID\",\"reservationEnabled\":true,\"feeNote\":\"Verified\",\"version\":null}";
        for (String role : List.of("USER", "OWNER")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/admin/api/reservation-places/123")
                    .with(authentication(role)).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/admin/api/reservation-places/123")
                .with(authentication("ADMIN")).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/admin/api/reservation-places/123")
                .with(authentication("ADMIN")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/reservation-places").with(authentication("ADMIN"))).andExpect(status().isOk());
        org.mockito.Mockito.verify(placeAdminService).update(org.mockito.ArgumentMatchers.eq("123"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

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
                .andExpect(redirectedUrl("/ko"));
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

    @Test
    void adminCanListOtherMembers_andRegularUserCannot() throws Exception {
        var members = List.of(
                AdminDto.UserItem.builder().id(1L).role("ADMIN").provider("google").build(),
                AdminDto.UserItem.builder().id(2L).role("USER").provider("kakao").build());
        when(adminService.getUsers(0, 10, null, null, null, null, null, null))
                .thenReturn(AdminDto.PageResponse.<AdminDto.UserItem>builder()
                        .items(members).page(0).size(10).totalElements(2).totalPages(1).build());
        mockMvc.perform(get("/admin/api/users").with(authentication("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.totalElements").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[1].id").value(2));
        mockMvc.perform(get("/admin/api/users").with(authentication("USER")))
                .andExpect(status().isForbidden());
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
