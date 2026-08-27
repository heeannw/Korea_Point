package com.example.demo.trip.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public class TripPlanDto {

    /** 목록 카드용 */
    @Data
    public static class Summary {
        private Long          id;
        private String        title;
        private int           travelDays;
        private LocalDate     startDate;
        private LocalDate     endDate;
        private List<String>  preferredAreas;
        private String        companion;
        private LocalDateTime createdAt;
        private String        tripStatus;
        private String        tripStatusLabel;
        private int           reservationCount;
        private int           requestedReservationCount;
        private int           confirmedReservationCount;
        private int           rejectedReservationCount;
        private int           cancelledReservationCount;
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
        private LocalDate     startDate;
        private int           startHour;
        private int           endHour;
        private String        arrivalAirport;
        private LocalTime     arrivalTime;
        private int           arrivalReadyMinutes;
        private String        departureAirport;
        private LocalTime     departureTime;
        private int           departureLeadMinutes;
        private String        intensity;
        private List<String>  preferredAreas;
        private List<String>  mustVisit;
        private List<String>  categories;
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
        private String imageUrl;
        private double avgRating;
        private int    reviewCount;
        private int    durationMinutes;
    }

    /** 저장 요청 — 추천 파라미터 + 추천 결과를 함께 전송 */
    @Data
    public static class SaveRequest {
        private String                title;    // null 이면 자동 생성
        private TripRecommendRequest  request;
        private TripRecommendResponse response;
    }

    @Data
    public static class TitleUpdateRequest {
        private String title;
    }
}
