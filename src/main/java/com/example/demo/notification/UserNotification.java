package com.example.demo.notification;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_notification", indexes = {
        @Index(name = "idx_notification_user_id", columnList = "user_id,id"),
        @Index(name = "idx_notification_user_read", columnList = "user_id,read_at")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Long reservationId;
    @Column(nullable = false, length = 200)
    private String placeTitle;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private NotificationType type;
    private Integer amount;
    private LocalDateTime paymentDeadline;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    void timestamp() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
