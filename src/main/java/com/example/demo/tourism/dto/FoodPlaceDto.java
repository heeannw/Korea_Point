package com.example.demo.tourism.dto;

import lombok.Data;

import java.util.List;

/**
 * foodmap 지도용 DTO
 * food_scores.json 의 음식점 데이터를 담음
 *
 * category 값: 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 바 / 기타
 */
@Data
public class FoodPlaceDto {

    // ── 공통 필드 ────────────────────────────────────────────
    private String  name;           // 장소명 (원본이름)
    private String  googleName;     // 구글 등록명
    private Double  lat;
    private Double  lng;
    private Double  avgStar;        // 평균 별점
    private Double  finalScore;     // AI 점수 (0~1)
    private Integer reviewCount;
    private Double  reviewMean;

    /** 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 바 / 기타 */
    private String  category;

    // ── 영어 번역 (food_scores_EN.json) ─────────────────────
    private String  nameEn;       // 영어 이름
    private String  categoryEn;   // 영어 카테고리

    // ── 런타임 계산 ──────────────────────────────────────────
    private Double  distance;       // 현위치 기준 거리(km)
    private List<ReviewItem> reviews;

    @Data
    public static class ReviewItem {
        private Double rating;
        private String content;
    }
}