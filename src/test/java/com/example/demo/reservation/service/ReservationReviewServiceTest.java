package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationReviewRequest;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.Optional;

import static com.example.demo.reservation.dto.ReservationReviewRequest.Decision.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationReviewServiceTest {
    private final TourReservationRepository reservations = mock(TourReservationRepository.class);
    private final ReservationPaymentRepository payments = mock(ReservationPaymentRepository.class);
    private final ReservationPlacePolicyRepository policies = mock(ReservationPlacePolicyRepository.class);
    private final ReservationPlacePolicyService policyService = mock(ReservationPlacePolicyService.class);
    private final ReservationReviewService service = new ReservationReviewService(reservations, payments, policies, policyService);

    @Test
    void approvalCreatesOnlyTheReviewedTotalWithDeadline() {
        var reservation = pending();
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
        LocalDateTime before = LocalDateTime.now();
        service.review(1L, 9L, new ReservationReviewRequest(APPROVE, 17000, "  Verified total  "));
        assertThat(reservation.getApplicationStatus()).isEqualTo("APPROVED");
        assertThat(reservation.getPayment().getAmount()).isEqualTo(17000);
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(reservation.isPayable()).isTrue();
        assertThat(reservation.getPaymentDeadline()).isBetween(before.plusHours(24), LocalDateTime.now().plusHours(24));
        assertThat(reservation.getReviewedBy()).isEqualTo(9L);
        assertThat(reservation.getReviewNote()).isEqualTo("Verified total");
        verify(policyService).requireApplicationAllowed("123");
    }

    @Test
    void deadlineNeverExceedsVisitTime() {
        var reservation = pending();
        LocalDateTime visit = LocalDateTime.now().plusHours(2).withSecond(0).withNano(0);
        reservation.setReservationDate(visit.toLocalDate());
        reservation.setReservationTime(visit.toLocalTime().toString());
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
        service.review(1L, 9L, new ReservationReviewRequest(APPROVE, 1000, "Verified"));
        assertThat(reservation.getPaymentDeadline()).isEqualTo(visit);
    }

    @Test
    void rejectionDoesNotCreatePaymentOrChangePlacePolicy() {
        var reservation = pending();
        service.review(1L, 9L, new ReservationReviewRequest(REJECT, null, "No availability"));
        assertThat(reservation.getApplicationStatus()).isEqualTo("REJECTED");
        assertThat(reservation.isPayable()).isFalse();
        assertThat(reservation.getPayment()).isNull();
        verifyNoInteractions(payments, policies, policyService);
    }

    @Test
    void freeDecisionClosesApplicationAndDisablesPlace() {
        var reservation = pending();
        service.review(1L, 9L, new ReservationReviewRequest(FREE, null, "Free admission verified"));
        assertThat(reservation.getApplicationStatus()).isEqualTo("FREE");
        assertThat(reservation.getPayment()).isNull();
        verify(policies).save(argThat(p -> p.getFeeStatus() == PlaceFeeStatus.FREE
                && !p.isReservationEnabled() && p.getContentId().equals("123") && p.getReviewedBy().equals(9L)));
        verifyNoInteractions(payments);
    }

    @Test
    void repeatedReviewAndCanceledApplicationAreRejected() {
        var reservation = pending();
        service.review(1L, 9L, new ReservationReviewRequest(REJECT, null, "Unavailable"));
        assertThatThrownBy(() -> service.review(1L, 9L, new ReservationReviewRequest(APPROVE, 1000, "Retry")))
                .isInstanceOf(ResponseStatusException.class);
        reservation.setReviewStatus(ReservationReviewStatus.PENDING);
        reservation.setStatus("예약 취소");
        assertThatThrownBy(() -> service.review(1L, 9L, new ReservationReviewRequest(APPROVE, 1000, "Retry")))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(payments);
    }

    @Test
    void pastVisitAndMissingTotalCannotBeApproved() {
        var reservation = pending();
        assertThatThrownBy(() -> service.review(1L, 9L, new ReservationReviewRequest(APPROVE, null, "Verified")))
                .isInstanceOf(ResponseStatusException.class);
        reservation.setReservationDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> service.review(1L, 9L, new ReservationReviewRequest(APPROVE, 1000, "Verified")))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(payments);
    }

    private TourReservation pending() {
        var reservation = TourReservation.builder().id(1L).userId(7L).contentId("123")
                .contentTypeId("39").placeTitle("Test place").peopleCount(3)
                .reservationDate(LocalDate.now().plusDays(3)).reservationTime("10:00")
                .reviewStatus(ReservationReviewStatus.PENDING).build();
        when(reservations.findLockedById(1L)).thenReturn(Optional.of(reservation));
        return reservation;
    }
}
