package com.example.demo.trip.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    /** 여행 기간과 겹치는 부산 축제 — 일정에 실제로 들어갔는지와 무관하게 항상 안내용으로 보여준다. */
    private List<FestivalDto> festivals;
    /**
     * 화면에 보여 줄 이름·주소(국문 → 화면 언어). 국문 화면이면 비어 있다.
     * 장소 이름 자체는 바꾸지 않는다 — 화면이 이름으로 대체 후보를 찾고 재조정 요청에 돌려보낸다.
     */
    private Map<String, String> displayText;
    /** displayText 의 언어. 화면 언어와 다르면 화면이 다시 받아 간다. */
    private String displayLang;

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
        private String contentId;
        private String contentTypeId;
        /** 화면 언어로 옮긴 이름·주소("근처 맛집" 처럼 목록만 따로 받을 때). 없으면 null. */
        private String displayName;
        private String displayAddress;
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

        // TourAPI 상세(운영시간·전화번호·메뉴 등) 조회용 — 큐레이션 장소는 빈 값이라 상세 조회 불가
        private String contentId;
        private String contentTypeId;

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
        private String category;
        private String address;
        private String imageUrl;
        private double avgRating;
        private int    reviewCount;
        private String contentId;
        private String contentTypeId;
    }

    @Data
    public static class FestivalDto {
        private String title;
        private LocalDate startDate;
        private LocalDate endDate;
        private String address;
        private String imageUrl;
        private double lat;
        private double lng;
    }
}
