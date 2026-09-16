package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.ReservationPaymentRepository;
import com.example.demo.reservation.event.ReservationConfirmedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationPaymentServiceTest {
    private final ReservationService reservations = mock(ReservationService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ReservationPlacePolicyService policies = mock(ReservationPlacePolicyService.class);
    private final PortOneGateway gateway = mock(PortOneGateway.class);
    private final ReservationPaymentRepository repository = mock(ReservationPaymentRepository.class);
    private final PortOneSettings settings = mock(PortOneSettings.class);
    private final ReservationPaymentService service = new ReservationPaymentService(reservations, events, policies, gateway, settings, repository);

    @Test
    void authenticatedProviderResultConfirmsOnceEvenWhenRetried() {
        var reservation = ready();
        when(gateway.lookup("imp_test")).thenReturn(remote("paid"));
        service.process(1L, 7L, request());
        service.process(1L, 7L, request());
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reservation.getPayment().getApprovalNo()).isEqualTo("TEST123");
        verify(gateway, times(2)).validate(any(), eq("imp_test"), eq("KPTEST-order"), eq(10000));
        verify(events, times(1)).publishEvent(any(ReservationConfirmedEvent.class));
    }

    @Test
    void forgedOrderCannotEvenTriggerProviderLookup() {
        ready();
        var request = request(); request.setMerchantUid("other-order");
        assertThatThrownBy(() -> service.process(1L, 7L, request)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(gateway, events);
    }

    @Test
    void failedOrUnpaidProviderResultNeverConfirms() {
        var reservation = ready();
        when(gateway.lookup("imp_test")).thenReturn(remote("failed"));
        service.process(1L, 7L, request());
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(events);
    }

    @Test
    void networkFailureDoesNotPretendPaymentSucceeded() {
        var reservation = ready();
        when(gateway.lookup("imp_test")).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> service.process(1L, 7L, request())).isInstanceOf(ResponseStatusException.class);
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        verifyNoInteractions(events);
    }

    @Test
    void canceledOrExpiredOrderIsRefundedInsteadOfConfirmed() {
        var reservation = ready();
        reservation.setStatus("예약 취소");
        when(gateway.lookup("imp_test")).thenReturn(remote("paid"));
        service.process(1L, 7L, request());
        verify(gateway).cancel("imp_test", "KPTEST-order", 10000);
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verifyNoInteractions(events);
    }

    @Test
    void paymentMadeAfterDeadlineIsRefunded() {
        var reservation = ready();
        reservation.setPaymentDeadline(LocalDateTime.now().minusHours(1));
        when(gateway.lookup("imp_test")).thenReturn(remote("paid"));
        service.process(1L, 7L, request());
        verify(gateway).cancel("imp_test", "KPTEST-order", 10000);
        verifyNoInteractions(events);
    }

    @Test
    void delayedWebhookForPaymentBeforeDeadlineStillConfirms() {
        var reservation = ready();
        reservation.setPaymentDeadline(LocalDateTime.now().minusMinutes(1));
        var remote = remote("paid"); remote.put("paid_at", Instant.now().minusSeconds(120).getEpochSecond());
        when(gateway.lookup("imp_test")).thenReturn(remote);
        service.process(1L, 7L, request());
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void changedPlacePolicyTriggersRefundAndNoConfirmation() {
        var reservation = ready();
        when(gateway.lookup("imp_test")).thenReturn(remote("paid"));
        doThrow(new PlaceBookingUnavailableException()).when(policies).requireApplicationAllowed("123");
        service.process(1L, 7L, request());
        assertThat(reservation.getPayment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(gateway).cancel("imp_test", "KPTEST-order", 10000);
        verifyNoInteractions(events);
    }

    @Test
    void preparationReusesServerOrderAndAmountAndNeverSendsSecrets() {
        var reservation = ready(); reservation.getPayment().setMerchantUid(null);
        when(settings.getUserCode()).thenReturn("imp_demo"); when(settings.getChannelKey()).thenReturn("channel-demo");
        var first = service.prepare(1L, 7L);
        var second = service.prepare(1L, 7L);
        assertThat(first.get("amount")).isEqualTo(10000);
        assertThat(first.get("merchantUid")).isEqualTo(second.get("merchantUid"));
        assertThat(first).doesNotContainKeys("apiKey", "apiSecret");
        verifyNoInteractions(gateway, events);
    }

    @Test
    void pendingRequestCannotOpenCheckout() {
        var reservation = ready(); reservation.setReviewStatus(ReservationReviewStatus.PENDING);
        assertThatThrownBy(() -> service.prepare(1L, 7L)).isInstanceOf(PlaceBookingUnavailableException.class);
        verifyNoInteractions(gateway);
    }

    private TourReservation ready() {
        var reservation = TourReservation.builder().id(1L).userId(7L).contentId("123").placeTitle("Test")
                .reservationDate(LocalDate.now().plusDays(3)).reservationTime("10:00").peopleCount(2)
                .reviewStatus(ReservationReviewStatus.APPROVED).paymentDeadline(LocalDateTime.now().plusHours(24)).build();
        reservation.setPayment(ReservationPayment.builder().reservation(reservation).amount(10000).merchantUid("KPTEST-order").build());
        when(reservations.findMineForUpdate(1L, 7L)).thenReturn(reservation);
        return reservation;
    }
    private ObjectNode remote(String state) {
        return new ObjectMapper().createObjectNode().put("status", state).put("paid_at", Instant.now().getEpochSecond())
                .put("apply_num", "TEST123").put("cancel_amount", 0);
    }
    private ReservationPaymentRequest request() {
        var request = new ReservationPaymentRequest(); request.setPaymentMethod(PaymentMethod.CARD);
        request.setImpUid("imp_test"); request.setMerchantUid("KPTEST-order"); return request;
    }
}
