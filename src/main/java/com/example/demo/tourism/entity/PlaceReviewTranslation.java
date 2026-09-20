package com.example.demo.tourism.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 방문 리뷰의 번역.
 *
 * <p>리뷰는 쓴 사람의 언어 그대로 저장한다. 일본어로 쓴 리뷰를 한국어 화면에서 열면
 * 일본어가 그대로 보여서, 보는 언어로 옮긴 문장을 여기에 쌓아 둔다.
 * 커뮤니티의 {@code community_translation}, 예약의 {@code reservation_place_translation}
 * 과 같은 방식이다.
 *
 * <p>{@code text} 가 비어 있으면 "옮길 필요가 없다"는 뜻이다. 리뷰가 이미 그 언어로
 * 쓰여 있거나 옮길 수 없는 경우인데, 줄은 남겨 두어야 보충 작업이 같은 리뷰를 다시
 * 붙잡지 않는다.
 */
@Entity
@Table(name = "place_review_translation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_place_review_translation",
                columnNames = {"review_id", "language"}),
        indexes = @Index(name = "idx_place_review_translation_lookup",
                columnList = "language, review_id"))
@Getter
@Setter
@NoArgsConstructor
public class PlaceReviewTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    /** ko / en / ja / zh / fr */
    @Column(nullable = false, length = 10)
    private String language;

    /** 옮긴 문장. 옮길 필요가 없으면 비어 있다. */
    @Column(columnDefinition = "text")
    private String text;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PlaceReviewTranslation(Long reviewId, String language, String text) {
        this.reviewId = reviewId;
        this.language = language;
        this.text = text;
        this.createdAt = LocalDateTime.now();
    }
}
