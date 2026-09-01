package com.example.demo.tourism.entity;

import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관광지·음식점·카페 상세에서 사용자가 남기는 방문 리뷰.
 * 한 사용자는 한 장소에 리뷰 하나(수정 시 덮어쓴다).
 * {@code content_id}는 KorService2 기준.
 */
@Entity
@Table(
        name = "place_review",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_place_review_user_content",
                columnNames = {"user_id", "content_id"}),
        indexes = @Index(name = "idx_place_review_content_created", columnList = "content_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "content_id", nullable = false, length = 20)
    private String contentId;

    @Column(name = "content_type_id", length = 5)
    private String contentTypeId;

    /** 1~5 */
    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String text;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = createdAt;   // 최초 저장 시엔 동일 → 이후 수정돼야 updatedAt > createdAt
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
