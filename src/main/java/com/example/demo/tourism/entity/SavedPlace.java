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
 * 사용자가 관광지·음식점·카페 상세에서 "저장(찜)"한 장소.
 *
 * <p>관광공사 장소는 DB에 담지 않는 게 이 프로젝트 방침이라(trip_plan_slot.placeName 등도 문자열),
 * 저장 시점의 국문 이름·주소·좌표를 스냅샷으로 넣어 둔다. 목록을 띄울 때 TourAPI를
 * 매번 다시 부르지 않기 위함이고, TourAPI에서 장소가 빠져도 목록은 유지된다.
 * 화면 표시는 언어에 맞춰 {@code TourPlaceI18n} 오버레이로 다시 번역한다.
 *
 * <p>{@code title}·{@code addr}는 <b>국문 원문</b>으로 저장한다. 여행 플래너가
 * {@code TripRecommendRequest.savedPlaceNames}를 국문 장소명과 문자열 매칭하기 때문이다.
 *
 * <p>{@code contentId}는 KorService2 기준이다. 관광/음식/카페 화면이 언어와 무관하게
 * KorService2만 조회하므로 저장한 사람의 언어와 상관없이 같은 장소는 같은 id다.
 */
@Entity
@Table(
        name = "saved_place",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_saved_place_user_content",
                columnNames = {"user_id", "content_id"}),
        indexes = @Index(
                name = "idx_saved_place_user_kind_created",
                columnList = "user_id, kind, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** TourAPI contentid (KorService2 기준). 상세 재조회·중복 판별의 실제 키. */
    @Column(name = "content_id", nullable = false, length = 20)
    private String contentId;

    /** 콘텐츠 유형 코드 (12 관광지 / 14 문화시설 / 39 음식점 …). 상세 조회에 필요. */
    @Column(name = "content_type_id", length = 5)
    private String contentTypeId;

    /** 목록 필터용 대분류: tourism | food | cafe. contentTypeId + foodCategory로 파생 가능하지만 저장해 둔다. */
    @Column(length = 10)
    private String kind;

    /** 저장 시점 국문 이름 스냅샷 (플래너 매칭용). */
    @Column(length = 200)
    private String title;

    /** 저장 시점 국문 주소 스냅샷. */
    @Column(length = 300)
    private String addr;

    private Double lat;

    private Double lng;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
