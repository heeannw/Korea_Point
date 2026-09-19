package com.example.demo.reservation.service;

import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.ApplicationEventPublisher;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationPlacePolicyServiceTest {
    private final ReservationPlacePolicyRepository repository = mock(ReservationPlacePolicyRepository.class);
    private final ReservationPlacePolicyService policyService = new ReservationPlacePolicyService(repository);

    @ParameterizedTest
    @EnumSource(value = PlaceFeeStatus.class, names = {"UNKNOWN", "FREE"})
    void freeAndUnknownCannotBeBookedEvenWithEnabledFlag(PlaceFeeStatus status) {
        ReservationPlacePolicy policy = policy(status, true);
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy));
        when(repository.findById("123")).thenReturn(Optional.of(policy));
        assertThatThrownBy(() -> policyService.requireBookable("123")).isInstanceOf(PlaceBookingUnavailableException.class);
        assertThat(policyService.policy("123").reservationAvailable()).isEqualTo(status == PlaceFeeStatus.UNKNOWN);
        assertThat(policyService.policy("123").depositPerPerson()).isNull();
    }

    @Test
    void missingOrDisabledPolicyFailsClosed() {
        when(repository.findForBooking("123")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policyService.requireBookable("123")).isInstanceOf(PlaceBookingUnavailableException.class);
        assertThat(policyService.policy("123").feeStatus()).isEqualTo(PlaceFeeStatus.UNKNOWN);
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.PAID, false)));
        assertThatThrownBy(() -> policyService.requireBookable("123")).isInstanceOf(PlaceBookingUnavailableException.class);
    }

    @Test
    void allowedPlaceCreatesApplicationWithoutPaymentButFreePlaceIsBlocked() {
        var reservations = mock(TourReservationRepository.class);
        var payments = mock(ReservationPaymentRepository.class);
        var service = new ReservationService(reservations, payments, policyService, mock(PortOneGateway.class), mock(com.example.demo.notification.NotificationService.class));
        var form = new ReservationFormDto(); form.setContentId("123"); form.setPeopleCount(2);
        form.setReservationDate(java.time.LocalDate.now().plusDays(3)); form.setReservationTime("10:00");
        // Spoofed category cannot override the policy bound to the content ID.
        form.setContentTypeId("39");
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.FREE, false)));
        assertThatThrownBy(() -> service.create(7L, form)).isInstanceOf(PlaceBookingUnavailableException.class);
        verifyNoInteractions(reservations, payments);
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.PAID, true)));
        when(reservations.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(7L, form).getPayment()).isNull();
        verifyNoInteractions(payments);
    }

    @Test
    void preparationIsBlockedAfterPolicyChanges() {
        var reservations = mock(ReservationService.class);
        var events = mock(ApplicationEventPublisher.class);
        var service = new ReservationPaymentService(reservations, events, policyService, mock(PortOneGateway.class), mock(PortOneSettings.class), mock(ReservationPaymentRepository.class), mock(com.example.demo.notification.NotificationService.class));
        var reservation = TourReservation.builder().contentId("123")
                .reviewStatus(ReservationReviewStatus.APPROVED)
                .paymentDeadline(java.time.LocalDateTime.now().plusHours(24)).build();
        var payment = ReservationPayment.builder().reservation(reservation).amount(5000).status(PaymentStatus.READY).build();
        reservation.setPayment(payment);
        when(reservations.findMineForUpdate(1L, 7L)).thenReturn(reservation);
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.FREE, false)));
        assertThatThrownBy(() -> service.prepare(1L, 7L))
                .isInstanceOf(PlaceBookingUnavailableException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getPaidAt()).isNull();
        verifyNoInteractions(events);
    }

    @Test
    void cancellationStillWorksForDisabledPlaces() {
        var reservations = mock(TourReservationRepository.class);
        var payments = mock(ReservationPaymentRepository.class);
        var reservation = TourReservation.builder().contentId("123").userId(7L).build();
        when(reservations.findLockedById(1L)).thenReturn(Optional.of(reservation));
        new ReservationService(reservations, payments, policyService, mock(PortOneGateway.class), mock(com.example.demo.notification.NotificationService.class)).cancel(1L, 7L);
        assertThat(reservation.getStatus()).isEqualTo("예약 취소");
        verifyNoInteractions(repository);
    }

    private ReservationPlacePolicy policy(PlaceFeeStatus status, boolean enabled) {
        var policy = new ReservationPlacePolicy(); policy.setContentId("123");
        policy.setFeeStatus(status); policy.setReservationEnabled(enabled); return policy;
    }

    @Test
    void unknownAndUnconfiguredPlacesAcceptRequestsButFreeAndDisabledPlacesDoNot() {
        policyService.requireApplicationAllowed("123");
        assertThat(policyService.policy("123").reservationAvailable()).isTrue();
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.UNKNOWN, false)));
        policyService.requireApplicationAllowed("123");
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.FREE, false)));
        assertThatThrownBy(() -> policyService.requireApplicationAllowed("123")).isInstanceOf(PlaceBookingUnavailableException.class);
        when(repository.findForBooking("123")).thenReturn(Optional.of(policy(PlaceFeeStatus.PAID, false)));
        assertThatThrownBy(() -> policyService.requireApplicationAllowed("123")).isInstanceOf(PlaceBookingUnavailableException.class);
    }
}
