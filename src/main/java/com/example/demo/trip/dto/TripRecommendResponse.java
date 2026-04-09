package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripRecommendResponse {

    /** 추천 관광지 목록 */
    private List<RecommendedPlace> tourismList;

    /** 추천 음식점 목록 */
    private List<RecommendedPlace> foodList;

    /** 추천 카페 목록 */
    private List<RecommendedPlace> cafeList;

    /** 일별 일정 */
    private List<DayScheduleDto> schedule;

    @Data
    public static class RecommendedPlace {
        private String name;
        private double score;
        private String reason;
        private String category;   // tourism / food / cafe / bar
        private Double lat;
        private Double lng;
        private String address;
        private String imageUrl;
        private double avgRating;
        private int reviewCount;
    }

    @Data
    public static class DayScheduleDto {
        private int day;
        private List<SlotDto> slots;
    }

    @Data
    public static class SlotDto {
        private String time;          // "15:00"
        private String category;      // tourism / food / cafe / bar
        private String placeName;
        private String reason;
        private double distanceKm;
        private Double lat;
        private Double lng;
        private String address;
        private String imageUrl;
        private double score;
        private List<AlternativeDto> alternatives;
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