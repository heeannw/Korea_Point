package com.example.demo.notification;

import com.example.demo.config.CurrentLanguage;
import com.example.demo.config.LocalizedMessages;
import com.example.demo.reservation.entity.TourReservation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final LocalizedMessages messages;

    public record Item(Long id, String title, String message, String placeTitle, String url,
                       Integer amount, LocalDateTime paymentDeadline, LocalDateTime createdAt, boolean read) {}
    public record Inbox(List<Item> items, long unreadCount, Long nextCursor) {}

    // Called inside the reservation transaction: a failed approval never creates an alert.
    @Transactional
    public void record(TourReservation reservation, NotificationType type) {
        notifications.save(UserNotification.builder().userId(reservation.getUserId())
                .reservationId(reservation.getId()).placeTitle(reservation.getPlaceTitle()).type(type)
                .amount(type == NotificationType.APPROVED ? reservation.getPayment().getAmount() : null)
                .paymentDeadline(type == NotificationType.APPROVED ? reservation.getPaymentDeadline() : null)
                .build());
    }

    public Inbox list(Long userId, Long before) {
        List<UserNotification> rows = notifications.findByUserIdAndIdLessThanOrderByIdDesc(userId,
                before == null ? Long.MAX_VALUE : before, PageRequest.of(0, 21));
        var page = rows.stream().limit(20).map(this::item).toList();
        return new Inbox(page, unreadCount(userId), rows.size() > 20 ? page.get(page.size() - 1).id() : null);
    }

    public long unreadCount(Long userId) { return notifications.countByUserIdAndReadAtIsNull(userId); }

    @Transactional
    public void markRead(Long userId, Long id) {
        if (!notifications.existsByIdAndUserId(id, userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        notifications.markRead(id, userId, LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(Long userId) { notifications.markAllRead(userId, LocalDateTime.now()); }

    private Item item(UserNotification notification) {
        String kind = notification.getType().name();
        String path = notification.getType() == NotificationType.APPROVED ? "payment/" : "requests/";
        return new Item(notification.getId(), messages.get("notification.type." + kind),
                messages.get("notification.message." + kind), notification.getPlaceTitle(),
                "/ko/reservation/" + path + notification.getReservationId() + "?lang=" + CurrentLanguage.get(),
                notification.getAmount(), notification.getPaymentDeadline(), notification.getCreatedAt(),
                notification.getReadAt() != null);
    }
}
