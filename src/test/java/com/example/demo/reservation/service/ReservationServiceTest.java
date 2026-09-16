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

    @Test
    void pastVisitIsAnInputErrorAndDoesNotSaveAnApplication() {
        var form = form(2);
        form.setReservationDate(LocalDate.now());
        form.setReservationTime("00:00");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(7L, form))
                .isInstanceOf(InvalidReservationTimeException.class);
        org.mockito.Mockito.verifyNoInteractions(reservationRepository, paymentRepository);
    }

    private final TourReservationRepository reservationRepository = mock(TourReservationRepository.class);
    private final ReservationPaymentRepository paymentRepository = mock(ReservationPaymentRepository.class);
    private final PortOneGateway gateway = mock(PortOneGateway.class);
    private final ReservationService service = new ReservationService(reservationRepository, paymentRepository, mock(ReservationPlacePolicyService.class), gateway);

    @Test
    void createsPendingApplicationWithoutPayment() {
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TourReservation reservation = service.create(7L, form(3));

        assertThat(reservation.getApplicationStatus()).isEqualTo("PENDING");
        assertThat(reservation.getPayment()).isNull();
        org.mockito.Mockito.verifyNoInteractions(paymentRepository);
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
        when(reservationRepository.findLockedById(3L)).thenReturn(Optional.of(reservation));

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

    @Test
    void providerCancellationFailureDoesNotMarkReservationRefunded() {
        var reservation = TourReservation.builder().id(3L).userId(7L).status("예약 완료")
                .reservationDate(LocalDate.now().plusDays(3)).reservationTime("10:00").build();
        var payment = ReservationPayment.builder().amount(10000).merchantUid("KPTEST-order")
                .impUid("imp_test").status(PaymentStatus.PAID).build();
        reservation.setPayment(payment);
        when(reservationRepository.findLockedById(3L)).thenReturn(Optional.of(reservation));
        when(gateway.cancel("imp_test", "KPTEST-order", 10000))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancel(3L, 7L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservation.getStatus()).isEqualTo("예약 완료");
        assertThat(payment.getRefundedAt()).isNull();
    }

    @Test
    void editingApprovedApplicationRequiresAnotherReviewAndCancelsOldPayment() {
        TourReservation reservation = TourReservation.builder().id(3L).userId(7L).contentId("123")
                .reviewStatus(com.example.demo.reservation.entity.ReservationReviewStatus.APPROVED)
                .reviewedBy(9L).reviewNote("Verified").paymentDeadline(java.time.LocalDateTime.now().plusHours(12)).build();
        ReservationPayment payment = ReservationPayment.builder().amount(15000).status(PaymentStatus.READY).build();
        reservation.setPayment(payment);
        when(reservationRepository.findLockedById(3L)).thenReturn(Optional.of(reservation));
        service.update(3L, 7L, form(4));
        assertThat(reservation.getApplicationStatus()).isEqualTo("PENDING");
        assertThat(reservation.getPeopleCount()).isEqualTo(4);
        assertThat(reservation.getReviewedBy()).isNull();
        assertThat(reservation.getPaymentDeadline()).isNull();
        assertThat(reservation.isPayable()).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void anotherUserCannotModifyOrCancelApplication() {
        var reservation = TourReservation.builder().id(3L).userId(8L).build();
        when(reservationRepository.findLockedById(3L)).thenReturn(Optional.of(reservation));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.update(3L, 7L, form(2)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.cancel(3L, 7L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reservation.getStatus()).isEqualTo("승인 대기");
    }
}
