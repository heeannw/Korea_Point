package com.example.demo.reservation.service;

import com.example.demo.config.SecurityConfig;
import com.example.demo.reservation.controller.PortOneController;
import com.example.demo.user.service.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers = PortOneController.class, excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.example.demo.community.config.CommunityUploadConfig.class))
@Import(SecurityConfig.class)
class PortOneSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean ReservationPaymentService payments;
    @MockBean CustomOAuth2UserService oauth;

    @Test
    void paymentPreparationRequiresLoginAndCsrf() throws Exception {
        mvc.perform(post("/api/reservations/1/payment/prepare").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reservations/1/payment/prepare").with(user("demo"))).andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void webhookAcceptsNewAndPreviouslyTruncatedOrderIds() throws Exception {
        for (String merchant : new String[]{"KPTEST-355e468f6a7d46159cf1679dfc453415", "KPTEST-355e468f-6a7d-4615-9cf1-679dfc453"}) {
            mvc.perform(post("/api/portone/webhook").contentType("application/json")
                    .content("{\"merchant_uid\":\"" + merchant + "\",\"imp_uid\":\"imp_test\"}"))
                    .andExpect(status().isOk());
            verify(payments).webhook(merchant, "imp_test");
        }
    }

    @Test
    void webhookAloneAcceptsAnonymousPostWithoutCsrf() throws Exception {
        String merchant = "KPTEST-12345678-1234-1234-1234-123456789abc";
        mvc.perform(post("/api/portone/webhook").contentType("application/json")
                .content("{\"merchant_uid\":\"" + merchant + "\",\"imp_uid\":\"imp_test\",\"status\":\"paid\",\"amount\":1}"))
                .andExpect(status().isOk());
        verify(payments).webhook(merchant, "imp_test");
        mvc.perform(post("/api/portone/webhook").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
