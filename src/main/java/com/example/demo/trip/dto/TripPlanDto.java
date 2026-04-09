package com.example.demo.trip.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

public class TripPlanDto {

    /** 목록 카드용 */
    @Data
    public static class Summary {
        private Long          id;
        private String        title;
        private int           travelDays;
        private String        preferredAreas;
        private String        companion;
        private LocalDateTime createdAt;
    }

    /** 상세 (일정 전체 포함) */
    @Data
    public static class Detail {
        private Long          id;
        private String        title;
        private int           age;
        private String        companion;
        private String        budget;
        private int           travelDays;
        private int           startHour;
        private int           endHour;
        private String        intensity;
        private String        preferredAreas;
        private String        mustVisit;
        private String        categories;
        private String        styleDescription;
        private LocalDateTime createdAt;
        private List<DayDto>  days;
    }

    @Data
    public static class DayDto {
        private int           day;
        private List<SlotDto> slots;
    }

    @Data
    public static class SlotDto {
        private int    slotOrder;
        private String time;
        private String category;
        private String placeName;
        private String reason;
        private double score;
        private Double lat;
        private Double lng;
        private String address;
    }

    /** 저장 요청 — 추천 파라미터 + 추천 결과를 함께 전송 */
    @Data
    public static class SaveRequest {
        private String                title;    // null 이면 자동 생성
        private TripRecommendRequest  request;
        private TripRecommendResponse response;
    }
}