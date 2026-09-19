package com.example.demo.reservation.service;

import com.example.demo.config.WebMvcConfig;
import com.example.demo.reservation.controller.ReservationController;
import com.example.demo.reservation.entity.*;
import com.example.demo.user.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import java.time.LocalDate;
import java.util.Locale;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReservationPolicyControllerTest {
    @Mock ReservationPlacePolicyService placePolicyService;
    @Mock ReservationService reservationService;
    @Mock TourApiReservationService tourApiReservationService;
    @Mock ReservationBrowseService reservationBrowseService;
    @InjectMocks ReservationController controller;

    @Test
    void placeListUsesReservationFilteringWithSearchAndCategory() throws Exception {
        when(reservationBrowseService.findPlaces("beach", "tourist_spot", "en", 2, 30))
                .thenReturn(java.util.List.of());
        mvc().perform(get("/api/tourapi/places").param("keyword", "beach")
                        .param("contentTypeId", "tourist_spot").param("lang", "en")
                        .param("pageNo", "2").param("numOfRows", "30"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(reservationBrowseService).findPlaces("beach", "tourist_spot", "en", 2, 30);
    }

    /** 실제 앱과 같은 언어 설정(세션에 저장, ?lang= 로 바꿈)으로 컨트롤러를 띄운다. */
    private org.springframework.test.web.servlet.MockMvc mvc() {
        var config = new WebMvcConfig();
        return MockMvcBuilders.standaloneSetup(controller)
                .setLocaleResolver(config.localeResolver())
                .addInterceptors(config.localeChangeInterceptor())
                .build();
    }

    @Test
    void formUsesSavedLanguageWhenLinkHasNoLang() throws Exception {
        // 일본어로 보던 사람이 lang 없는 링크로 들어와도 일본어로 그린다.
        var place = com.example.demo.reservation.dto.TourApiPlaceDto.builder().title("Place").contentId("123").build();
        when(tourApiReservationService.findDetail(eq("123"), any(), eq("ja"))).thenReturn(place);
        try (var security = mockStatic(SecurityUtil.class)) {
            mvc().perform(get("/ko/reservation/new").param("contentId", "123")
                            .sessionAttr(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, Locale.JAPANESE))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("lang", "ja"));
        }
    }

    @Test
    void staleLangInPostedFormDoesNotChangeLanguage() throws Exception {
        // 다른 탭에서 일본어로 바꾼 뒤, 한국어일 때 열어 둔 폼을 보내도 일본어가 유지된다.
        var place = com.example.demo.reservation.dto.TourApiPlaceDto.builder().title("Place").contentId("123").build();
        when(tourApiReservationService.findDetail(eq("123"), any(), eq("ja"))).thenReturn(place);
        when(reservationService.create(eq(7L), any())).thenThrow(new InvalidReservationTimeException());
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc().perform(post("/ko/reservation/complete").param("contentId", "123").param("placeTitle", "Place")
                            .param("lang", "ko")
                            .param("reservationDate", LocalDate.now().toString()).param("reservationTime", "00:00")
                            .param("peopleCount", "2")
                            .sessionAttr(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, Locale.JAPANESE))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("lang", "ja"))
                    .andExpect(request().sessionAttribute(SessionLocaleResolver.LOCALE_SESSION_ATTRIBUTE_NAME, Locale.JAPANESE));
        }
    }

    @Test
    void newFormDefaultsToAFutureVisit() throws Exception {
        var place = com.example.demo.reservation.dto.TourApiPlaceDto.builder().title("Place").contentId("123").build();
        when(tourApiReservationService.findDetail(eq("123"), any(), eq("ko"))).thenReturn(place);
        var mvc = mvc();
        try (var security = mockStatic(SecurityUtil.class)) {
            mvc.perform(get("/ko/reservation/new").param("contentId", "123"))
                    .andExpect(status().isOk()).andExpect(view().name("reservation/reservationForm"))
                    .andExpect(model().attribute("reservationForm", org.hamcrest.Matchers.hasProperty(
                            "reservationDate", org.hamcrest.Matchers.is(LocalDate.now().plusDays(1)))));
        }
    }

    @Test
    void pastVisitSubmissionKeepsInputAndShowsTimeErrorInsteadOfPlaceRestriction() throws Exception {
        var place = com.example.demo.reservation.dto.TourApiPlaceDto.builder().title("Place").contentId("123").build();
        when(tourApiReservationService.findDetail(eq("123"), any(), eq("ko"))).thenReturn(place);
        when(reservationService.create(eq(7L), any())).thenThrow(new InvalidReservationTimeException());
        var mvc = mvc();
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc.perform(post("/ko/reservation/complete").param("contentId", "123").param("placeTitle", "Place")
                    .param("reservationDate", LocalDate.now().toString()).param("reservationTime", "00:00")
                    .param("peopleCount", "2").param("memo", "Keep my input"))
                    .andExpect(status().isOk()).andExpect(view().name("reservation/reservationForm"))
                    .andExpect(model().attributeHasFieldErrorCode("reservationForm", "reservationTime", "reservation.form.futureVisit"))
                    .andExpect(model().attribute("reservationForm", org.hamcrest.Matchers.hasProperty("memo", org.hamcrest.Matchers.is("Keep my input"))));
        }
    }

    @Test
    void futureVisitSubmissionRedirectsToPendingRequest() throws Exception {
        var place = com.example.demo.reservation.dto.TourApiPlaceDto.builder().title("Place").contentId("123").build();
        when(tourApiReservationService.findDetail(eq("123"), any(), eq("ko"))).thenReturn(place);
        when(reservationService.create(eq(7L), any())).thenReturn(TourReservation.builder().id(12L).build());
        var mvc = mvc();
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc.perform(post("/ko/reservation/complete").param("contentId", "123").param("placeTitle", "Place")
                    .param("reservationDate", LocalDate.now().plusDays(1).toString()).param("reservationTime", "10:00")
                    .param("peopleCount", "2"))
                    .andExpect(redirectedUrl("/ko/reservation/requests/12"));
        }
    }

    @Test
    void pastVisitUpdateReturnsInputError() throws Exception {
        when(reservationService.update(eq(1L), eq(7L), any())).thenThrow(new InvalidReservationTimeException());
        var mvc = mvc();
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc.perform(patch("/api/reservations/1").contentType("application/json")
                    .content("{\"contentId\":\"123\",\"placeTitle\":\"Place\",\"reservationDate\":\"" + LocalDate.now() + "\",\"reservationTime\":\"00:00\",\"peopleCount\":2}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_VISIT_TIME"));
        }
    }

    @Test
    void directFormAndCreationRequestsAreBlockedBeforeRenderingOrWriting() throws Exception {
        doThrow(new PlaceBookingUnavailableException()).when(placePolicyService).requireApplicationAllowed("123");
        var mvc = mvc();
        mvc.perform(get("/ko/reservation/new").param("contentId", "123").param("lang", "en"))
                .andExpect(status().isConflict()).andExpect(view().name("reservation/reservationUnavailable"))
                .andExpect(model().attribute("lang", "en"));
        mvc.perform(post("/ko/reservation/complete").param("contentId", "123").param("placeTitle", "Place")
                        .param("reservationDate", LocalDate.now().plusDays(1).toString()).param("reservationTime", "10:00")
                        .param("peopleCount", "2"))
                .andExpect(status().isConflict()).andExpect(view().name("reservation/reservationUnavailable"));
        verifyNoInteractions(reservationService);
    }

    @Test
    void directPaymentPageAndSubmissionRecheckPolicy() throws Exception {
        doThrow(new PlaceBookingUnavailableException()).when(placePolicyService).requireApplicationAllowed("123");
        var reservation = TourReservation.builder().contentId("123").language("ko")
                .reviewStatus(ReservationReviewStatus.APPROVED)
                .paymentDeadline(java.time.LocalDateTime.now().plusHours(24)).build();
        reservation.setPayment(ReservationPayment.builder().amount(12000).status(PaymentStatus.READY).build());
        when(reservationService.findMine(1L, 7L)).thenReturn(reservation);
        var mvc = mvc();
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc.perform(get("/ko/reservation/payment/1")).andExpect(status().isConflict());
            mvc.perform(post("/ko/reservation/payment/1").param("paymentMethod", "CARD"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    void reservationUpdateApiReturnsStructuredConflict() throws Exception {
        when(reservationService.update(eq(1L),eq(7L),any())).thenThrow(new PlaceBookingUnavailableException());
        var mvc = mvc();
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc.perform(patch("/api/reservations/1").contentType("application/json")
                    .content("{\"contentId\":\"123\",\"placeTitle\":\"Place\",\"reservationDate\":\"" + LocalDate.now().plusDays(1) + "\",\"reservationTime\":\"10:00\",\"peopleCount\":2}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLACE_NOT_BOOKABLE"));
        }
    }
}
