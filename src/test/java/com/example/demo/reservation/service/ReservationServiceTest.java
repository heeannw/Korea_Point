package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.entity.ReservationPayment;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.repository.ReservationPaymentRepository;
import com.example.demo.reservation.repository.TourReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationServiceTest {

    private final TourReservationRepository reservationRepository = mock(TourReservationRepository.class);
    private final ReservationPaymentRepository paymentRepository = mock(ReservationPaymentRepository.class);
    private final ReservationService service = new ReservationService(reservationRepository, paymentRepository);

    @Test
    void createsPerPersonDepositTogetherWithPendingReservation() {
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TourReservation reservation = service.create(7L, form(3));

        assertThat(reservation.getStatus()).isEqualTo("결제 대기");
        assertThat(reservation.getPayment()).isNotNull();
        assertThat(reservation.getPayment().getAmount()).isEqualTo(15_000);
    }

    @Test
    void cancellationBeforeDeadlineRefundsPaidDeposit() {
        TourReservation reservation = TourReservation.builder()
                .id(3L)
                .userId(7L)
                .reservationDate(LocalDate.now().plusDays(3))
                .reservationTime("18:00")
                .status("예약 완료")
                .build();
        ReservationPayment payment = ReservationPayment.builder()
                .reservation(reservation)
                .amount(10_000)
                .status(PaymentStatus.PAID)
                .build();
        reservation.setPayment(payment);
        when(reservationRepository.findByIdAndUserId(3L, 7L)).thenReturn(Optional.of(reservation));

        service.cancel(3L, 7L);

        assertThat(reservation.getStatus()).isEqualTo("예약 취소");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
    }

    private ReservationFormDto form(int peopleCount) {
        ReservationFormDto form = new ReservationFormDto();
        form.setContentId("123");
        form.setContentTypeId("39");
        form.setPlaceTitle("테스트 식당");
        form.setReservationDate(LocalDate.now().plusDays(3));
        form.setReservationTime("18:00");
        form.setPeopleCount(peopleCount);
        return form;
    }
}
