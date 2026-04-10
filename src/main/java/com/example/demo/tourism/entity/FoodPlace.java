package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 음식점 엔티티
 * food_scores.json 의 카테고리와 동일한 값 사용:
 * 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 바 / 기타
 *
 * ※ 현재 서비스는 food_scores.json 을 메모리에서 직접 사용하므로
 *    이 엔티티는 DB 마이그레이션 시 활용 예정
 */
@Entity
@Table(name = "food_place")
@Getter @Setter
@NoArgsConstructor
public class FoodPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 이름 (필수) */
    @Column(nullable = false)
    private String name;

    /** 구글 등록명 / 부제목 */
    private String googleName;

    private Double lat;
    private Double lng;

    /** 평균 별점 */
    private Double avgStar;

    /** AI 추천 점수 0~1 */
    @Column(name = "final_score")
    private Double finalScore;

    private Integer reviewCount;
    private Double  reviewMean;

    /**
     * 업종 카테고리: 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 바 / 기타
     * ✅ 오타 수정: catrgory → category
     */
    @Column(length = 30)
    private String category;   // ✅ 수정: catrgory → category
}