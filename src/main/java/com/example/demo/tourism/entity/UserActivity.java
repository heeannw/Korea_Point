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
 * 관광 개인화 추천용 사용자 행동 로그. append-only.
 *
 * <p>프론트가 명시적으로 {@code POST /tour/api/activity} 로 남긴다(관광 목록·상세가 클라 렌더라
 * 서버가 클릭을 모름). 점수 계산에는 최근 N건만 쓰고, 오래된 건은 배치로 정리한다.
 * 비로그인 사용자의 행동은 저장하지 않는다.</p>
 */
@Entity
@Table(
        name = "user_activity",
        indexes = @Index(name = "idx_user_activity_user_created", columnList = "user_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActivityType action;

    /** 장소 행동(DETAIL_VIEW·SAVE)일 때 TourAPI contentId. */
    @Column(name = "content_id", length = 20)
    private String contentId;

    @Column(name = "content_type_id", length = 5)
    private String contentTypeId;

    /** CATEGORY 액션이면 칩 슬러그, 장소 행동이면 그 장소 소분류명 스냅샷(유사 태그). */
    @Column(name = "category_slug", length = 40)
    private String categorySlug;

    /** SEARCH 액션일 때 검색어. */
    @Column(length = 100)
    private String keyword;

    /** 구 이름(주소·좌표에서 파생, 스냅샷). 지역 선호 계산용. */
    @Column(length = 20)
    private String area;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
