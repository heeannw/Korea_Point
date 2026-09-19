package com.example.demo.notification;

import com.example.demo.config.LocalizedMessages;
import com.example.demo.reservation.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repository, messages());

    private LocalizedMessages messages() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages"); source.setDefaultEncoding("UTF-8");
        return new LocalizedMessages(source);
    }

    @Test
    void approvalRecordsOwnerPaymentAndDeadline() {
        var deadline = LocalDateTime.now().plusHours(24);
        var reservation = TourReservation.builder().id(12L).userId(7L).placeTitle("Hotel")
                .paymentDeadline(deadline).payment(ReservationPayment.builder().amount(17000).build()).build();
        service.record(reservation, NotificationType.APPROVED);
        verify(repository).save(argThat(n -> n.getUserId().equals(7L) && n.getReservationId().equals(12L)
                && n.getType() == NotificationType.APPROVED && n.getAmount() == 17000
                && n.getPaymentDeadline().equals(deadline) && n.getReadAt() == null));
    }

    @Test
    void cursorPaginationStaysScopedToOwnerAndProvidesPaymentDestination() {
        var rows = IntStream.range(0, 21).mapToObj(i -> UserNotification.builder().id(99L-i).userId(7L)
                .reservationId(12L).type(NotificationType.APPROVED).placeTitle("Hotel").createdAt(LocalDateTime.now()).build()).toList();
        when(repository.findByUserIdAndIdLessThanOrderByIdDesc(eq(7L), eq(100L), any())).thenReturn(rows);
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(25L);
        var inbox = service.list(7L, 100L);
        assertThat(inbox.items()).hasSize(20);
        assertThat(inbox.nextCursor()).isEqualTo(80L);
        assertThat(inbox.unreadCount()).isEqualTo(25L);
        assertThat(inbox.items().get(0).url()).startsWith("/ko/reservation/payment/12?lang=");
        assertThat(inbox.items().get(0).title()).doesNotContain("notification.type.");
    }

    @Test
    void cannotReadAnotherUsersNotification() {
        when(repository.existsByIdAndUserId(12L, 7L)).thenReturn(false);
        assertThatThrownBy(() -> service.markRead(7L, 12L)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        verify(repository, never()).markRead(any(), any(), any());
    }

    @Test
    void readingIsIdempotentAndAllReadIsOwnerScoped() {
        when(repository.existsByIdAndUserId(12L, 7L)).thenReturn(true);
        service.markRead(7L, 12L);
        service.markRead(7L, 12L);
        verify(repository, times(2)).markRead(eq(12L), eq(7L), any());
        service.markAllRead(7L);
        verify(repository).markAllRead(eq(7L), any());
    }
}
