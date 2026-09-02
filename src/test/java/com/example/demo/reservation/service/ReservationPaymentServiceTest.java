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
    private final ReservationPaymentService paymentService = new ReservationPaymentService(reservationService, eventPublisher);

    @Test
    void successfulPaymentConfirmsReservation() {
        TourReservation reservation = reservationWithReadyPayment();
        when(reservationService.findMine(1L, 7L)).thenReturn(reservation);

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
                .reservationDate(java.time.LocalDate.of(2026, 9, 10))
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

    private ReservationPaymentRequest request() {
        ReservationPaymentRequest request = new ReservationPaymentRequest();
        request.setPaymentMethod(PaymentMethod.CARD);
        return request;
    }
}
