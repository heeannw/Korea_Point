package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripRecommendResponse {

    private List<RecommendedPlace> tourismList;
    private List<RecommendedPlace> foodList;
    private List<RecommendedPlace> cafeList;
    private List<DayScheduleDto>   schedule;

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
        private int             day;
        private List<SlotDto>   slots;
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