package com.example.demo.reservation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_place_translation",
        uniqueConstraints = @UniqueConstraint(columnNames = {"content_id", "language"}))
@Getter
@Setter
@NoArgsConstructor
public class ReservationPlaceTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content_id", nullable = false, length = 50)
    private String contentId;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 64)
    private String sourceHash;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(columnDefinition = "TEXT")
    private String operatingInformation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationTranslationStatus status;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime nextRetryAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
