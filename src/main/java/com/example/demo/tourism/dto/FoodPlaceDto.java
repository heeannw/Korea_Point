package com.example.demo.tourism.dto;

import lombok.Data;
import java.util.List;

/**
 * foodmap 통합 지도용 DTO
 * 카페(cafe_scores.json)와 음식점(DB) 모두를 동일한 형태로 담음
 */
@Data
public class FoodPlaceDto {

    // ── 공통 필드 ────────────────────────────────────────────
    private String  name;           // 장소명
    private String  googleName;     // 구글 등록명 (카페) / 부제목 (음식점)
    private Double  lat;
    private Double  lng;
    private Double  avgStar;        // 평균 별점
    private Double  finalScore;     // AI 점수 (0~1)
    private Integer reviewCount;
    private Double  reviewMean;

    /** "CAFE" | "RESTAURANT" */
    private String  category;

    // ── 런타임 계산 ──────────────────────────────────────────
    private Double  distance;       // 현위치 기준 거리(km)
    private List<ReviewItem> reviews;

    @Data
    public static class ReviewItem {
        private Double rating;
        private String content;
    }
}