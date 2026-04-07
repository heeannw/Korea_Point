package com.example.demo.tourism.dto;

import lombok.Data;
import java.util.List;

/**
 * cafe_scores.json 한 항목과 1:1 매핑
 * + 리뷰 목록 추가 (review_cafe.csv 에서 병합)
 */
@Data
public class CafeDto {

    // ── cafe_scores.json 필드 ────────────────────────────────
    private String name;          // 원본이름
    private String googleName;    // 구글이름
    private Double lat;
    private Double lng;
    private Double avgStar;       // 평균별점
    private Double finalScore;    // AI 점수 (0~1)
    private Integer reviewCount;
    private Double reviewMean;

    // ── 런타임 계산 ──────────────────────────────────────────
    private Double distance;      // 현위치 기준 거리(km)
    private List<ReviewItem> reviews;

    @Data
    public static class ReviewItem {
        private Double rating;
        private String content;
    }
}