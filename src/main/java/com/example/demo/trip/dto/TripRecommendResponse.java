package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripRecommendResponse {

    private List<RecommendedPlace> tourismList;
    private List<RecommendedPlace> foodList;
    private List<RecommendedPlace> cafeList;
    private List<DayScheduleDto>   schedule;
    private String aiSummary;
    private String weatherWarning;
    private AirQualitySummary airQuality;
    private String forecastIssuedAt;
    private HealthRiskSummary healthRisk;

    @Data
    public static class HealthRiskSummary {
        private Integer uvIndex;
        private String uvLevel;
        private String heatRisk;
        private String coldRisk;
    }

    @Data
    public static class AirQualitySummary {
        private int pm10;
        private int pm25;
        private String grade;
        private String measuredAt;
    }

    @Data
    public static class RecommendedPlace {
        private String name;
        private double score;
        private String reason;
        private String category;
        private Double lat;
        private Double lng;
        private String address;
        private String imageUrl;
        private double avgRating;
        private int    reviewCount;
    }

    @Data
    public static class DayScheduleDto {
        private int           day;
        private List<SlotDto> slots;
        private WeatherSummary weather;
    }

    @Data
    public static class WeatherSummary {
        private double temperature;
        private int rainProbability;
        private double windSpeed;
        private String precipitationType;
        private String sky;
    }

    @Data
    public static class SlotDto {
        private String time;
        private String category;
        private String placeName;
        private String reason;
        private double distanceKm;
        private Double lat;
        private Double lng;
        private double score;

        // ── 상세 정보 (패널에 표시) ──
        private String address;       // 한국어 주소
        private String imageUrl;      // 대표 이미지
        private double avgRating;     // 평균 별점
        private int    reviewCount;   // 리뷰 수
        private int    durationMinutes;
        private boolean locked;
        private String lockReason;

        /** 리뷰 목록 (최대 3개) */
        private List<ReviewDto> reviews;

        private List<AlternativeDto> alternatives;
    }

    @Data
    public static class ReviewDto {
        private String author;    // 작성자
        private double rating;    // 별점
        private String text;      // 내용
        private String timeDesc;  // "3 years ago" 또는 날짜
    }

    @Data
    public static class AlternativeDto {
        private String placeName;
        private double score;
        private double distanceKm;
        private Double lat;
        private Double lng;
    }
}
