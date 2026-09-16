package com.example.demo.reservation.service;

import com.example.demo.reservation.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationRequestViewTest {
    @Test
    void requestStatesRenderInEverySupportedLanguageWithoutMissingMessages() throws Exception {
        var context = new StaticWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.refresh();
        try {
            var messages = new ResourceBundleMessageSource();
            messages.setBasename("messages");
            messages.setDefaultEncoding("UTF-8");
            var resolver = new SpringResourceTemplateResolver();
            resolver.setApplicationContext(context);
            resolver.setPrefix("classpath:/templates/");
            resolver.setSuffix(".html");
            resolver.setCharacterEncoding("UTF-8");
            var engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            engine.setTemplateEngineMessageSource(messages);
            var views = new ThymeleafViewResolver();
            views.setTemplateEngine(engine);
            views.setCharacterEncoding("UTF-8");
            var mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).setViewResolvers(views).build();
            for (String language : new String[]{"ko", "en", "ja", "zh-CN", "fr"}) {
                for (String state : new String[]{"PENDING", "APPROVED", "REJECTED", "FREE", "EXPIRED", "CANCELED"}) {
                    String html = mvc.perform(get("/fixture/" + state + "/" + language).locale(Locale.forLanguageTag(language)))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
                    assertThat(html).doesNotContain("??", "<script>alert");
                    assertThat(html.contains("/ko/reservation/payment/1")).isEqualTo(state.equals("APPROVED"));
                    assertThat(html).contains("&lt;script&gt;alert");
                }
                for (String state : new String[]{"PAYMENT_ENABLED", "PAYMENT_DISABLED", "RETURN", "COMPLETE"}) {
                    String html = mvc.perform(get("/fixture/" + state + "/" + language).locale(Locale.forLanguageTag(language)))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
                    assertThat(html).doesNotContain("??", "apiSecret", "api-key");
                    assertThat(html).doesNotContain("시연용 테스트", "테스트 결제", "Demo test payment", "Test card payment");
                    if (state.equals("PAYMENT_DISABLED")) assertThat(html).contains("disabled=\"disabled\"").doesNotContain("https://cdn.iamport.kr");
                    if (state.equals("PAYMENT_ENABLED")) assertThat(html).contains("https://cdn.iamport.kr/v1/iamport.js");
                    if (state.equals("COMPLETE")) assertThat(html).contains("17,000", "Test place");
                    if (state.equals("COMPLETE") && language.equals("ko")) assertThat(html).contains("결제가 완료되었습니다.");
                }
            }
        } finally {
            context.close();
        }
    }

    @Controller
    static class FixtureController {
        @GetMapping("/fixture/{state}/{language}")
        String view(@PathVariable String state, @PathVariable String language, Model model) {
            var reservation = TourReservation.builder().id(1L).reservationNo("KP-TEST")
                    .placeTitle("Test place").reservationDate(LocalDate.now().plusDays(3))
                    .reservationTime("10:00").peopleCount(2).reviewNote("<script>alert(1)</script>").build();
            if (state.equals("CANCELED")) reservation.setStatus("예약 취소");
            else if (state.equals("APPROVED") || state.equals("EXPIRED") || state.startsWith("PAYMENT") || state.equals("RETURN") || state.equals("COMPLETE")) {
                reservation.setReviewStatus(ReservationReviewStatus.APPROVED);
                reservation.setPaymentDeadline(LocalDateTime.now().plusHours(state.equals("EXPIRED") ? -1 : 24));
                reservation.setPayment(ReservationPayment.builder().amount(17000).status(PaymentStatus.READY).build());
            } else reservation.setReviewStatus(ReservationReviewStatus.valueOf(state));
            model.addAttribute("reservation", reservation);
            model.addAttribute("lang", language);
            model.addAttribute("isLoggedIn", false);
            if (state.equals("COMPLETE")) {
                reservation.getPayment().setStatus(PaymentStatus.PAID);
                reservation.setStatus("예약 완료");
                model.addAttribute("payment", reservation.getPayment());
                model.addAttribute("reservationNo", reservation.getReservationNo());
                return "reservation/reservationComplete";
            }
            if (state.startsWith("PAYMENT") || state.equals("RETURN")) {
                model.addAttribute("_csrf", new org.springframework.security.web.csrf.DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token"));
                model.addAttribute("payment", reservation.getPayment());
                model.addAttribute("paymentRequest", new com.example.demo.reservation.dto.ReservationPaymentRequest());
                model.addAttribute("portOneReady", state.equals("PAYMENT_ENABLED"));
                return state.equals("RETURN") ? "reservation/paymentReturn" : "reservation/reservationPayment";
            }
            return "reservation/reservationRequest";
        }
    }
}
