package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 음식점 엔티티
 * cafe_scores.json 과 동일한 구조로 설계해 FoodMapService 에서 통합 처리
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

    /** 평균 별점 (구글 등 외부 수집) */
    private Double avgStar;

    /** AI 추천 점수 0~1 */
    @Column(name = "final_score")
    private Double finalScore;

    private Integer reviewCount;
    private Double  reviewMean;

    /**
     * 업종 대분류: KOREAN, CHINESE, JAPANESE, WESTERN, FAST_FOOD,
     *              BUFFET, CHICKEN, PIZZA, BURGER, SEAFOOD, MEAT, ETC
     */
    @Column(length = 30)
    private String cuisine;
}