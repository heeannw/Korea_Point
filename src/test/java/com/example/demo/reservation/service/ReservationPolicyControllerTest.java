package com.example.demo.reservation.service;

import com.example.demo.reservation.controller.ReservationController;
import com.example.demo.reservation.entity.*;
import com.example.demo.user.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ReservationPolicyControllerTest {
    @Mock ReservationPlacePolicyService placePolicyService;
    @Mock ReservationService reservationService;
    @Mock TourApiReservationService tourApiReservationService;
    @InjectMocks ReservationController controller;

    @Test
    void newFormDefaultsToAFutureVisit() throws Exception {
        var place = com.example.demo.reservation.dto.TourApiPlaceDto.builder().title("Place").contentId("123").build();
        when(tourApiReservationService.findDetail(eq("123"), any(), eq("ko"))).thenReturn(place);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
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
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        try (var security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(7L);
            mvc.perform(patch("/api/reservations/1").contentType("application/json")
                    .content("{\"contentId\":\"123\",\"placeTitle\":\"Place\",\"reservationDate\":\"" + LocalDate.now().plusDays(1) + "\",\"reservationTime\":\"10:00\",\"peopleCount\":2}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLACE_NOT_BOOKABLE"));
        }
    }
}
