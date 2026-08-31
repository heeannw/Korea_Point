package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.entity.PaymentMethod;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.entity.ReservationPayment;
import com.example.demo.reservation.entity.TourReservation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationPaymentServiceTest {

    private final ReservationService reservationService = mock(ReservationService.class);
    private final ReservationPaymentService paymentService = new ReservationPaymentService(reservationService);

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
    }

    private TourReservation reservationWithReadyPayment() {
        TourReservation reservation = TourReservation.builder().id(1L).status("결제 대기").build();
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
