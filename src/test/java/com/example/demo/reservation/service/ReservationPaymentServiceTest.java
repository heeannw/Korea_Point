package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.entity.PaymentMethod;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.entity.ReservationPayment;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.event.ReservationConfirmedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationPaymentServiceTest {

    private final ReservationService reservationService = mock(ReservationService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final ReservationPaymentService paymentService = new ReservationPaymentService(reservationService, eventPublisher, mock(ReservationPlacePolicyService.class));

    @Test
    void successfulPaymentConfirmsReservation() {
        TourReservation reservation = reservationWithReadyPayment();
        when(reservationService.findMineForUpdate(1L, 7L)).thenReturn(reservation);

        ReservationPayment payment = paymentService.process(1L, 7L, request());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(payment.getApprovalNo()).startsWith("KPAY-");
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo("예약 완료");
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(ReservationConfirmedEvent.class));
    }

    private TourReservation reservationWithReadyPayment() {
        TourReservation reservation = TourReservation.builder()
                .id(1L)
                .userId(7L)
                .reservationNo("KP-20260902-TEST")
                .placeTitle("테스트 관광지")
                .reservationDate(java.time.LocalDate.now().plusDays(3))
                .reviewStatus(com.example.demo.reservation.entity.ReservationReviewStatus.APPROVED)
                .paymentDeadline(java.time.LocalDateTime.now().plusHours(24))
                .reservationTime("10:00")
                .peopleCount(2)
                .status("결제 대기")
                .build();
        ReservationPayment payment = ReservationPayment.builder()
                .reservation(reservation)
                .amount(10_000)
                .build();
        reservation.setPayment(payment);
        return reservation;
    }

    @Test
    void pendingExpiredAndCanceledApplicationsCannotPay() {
        TourReservation reservation = reservationWithReadyPayment();
        when(reservationService.findMineForUpdate(1L, 7L)).thenReturn(reservation);
        reservation.setReviewStatus(com.example.demo.reservation.entity.ReservationReviewStatus.PENDING);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentService.process(1L, 7L, request()))
                .isInstanceOf(PlaceBookingUnavailableException.class);
        reservation.setReviewStatus(com.example.demo.reservation.entity.ReservationReviewStatus.APPROVED);
        reservation.setPaymentDeadline(java.time.LocalDateTime.now().minusSeconds(1));
        assertThat(reservation.getApplicationStatus()).isEqualTo("EXPIRED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentService.process(1L, 7L, request()))
                .isInstanceOf(PlaceBookingUnavailableException.class);
        reservation.setPaymentDeadline(java.time.LocalDateTime.now().plusHours(1));
        reservation.setStatus("예약 취소");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentService.process(1L, 7L, request()))
                .isInstanceOf(PlaceBookingUnavailableException.class);
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        org.mockito.Mockito.verifyNoInteractions(eventPublisher);
    }

    @Test
    void repeatedPaymentDoesNotPublishAnotherConfirmation() {
        TourReservation reservation = reservationWithReadyPayment();
        when(reservationService.findMineForUpdate(1L, 7L)).thenReturn(reservation);
        var paid = paymentService.process(1L, 7L, request());
        String approval = paid.getApprovalNo();
        assertThat(paymentService.process(1L, 7L, request())).isSameAs(paid);
        assertThat(paid.getApprovalNo()).isEqualTo(approval);
        verify(eventPublisher, org.mockito.Mockito.times(1)).publishEvent(org.mockito.ArgumentMatchers.any(ReservationConfirmedEvent.class));
    }

    private ReservationPaymentRequest request() {
        ReservationPaymentRequest request = new ReservationPaymentRequest();
        request.setPaymentMethod(PaymentMethod.CARD);
        return request;
    }
}
