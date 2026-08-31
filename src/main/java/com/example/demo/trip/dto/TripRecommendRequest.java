package com.example.demo.trip.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class TripRecommendRequest {

    private int age;
    private String companion;   // solo / friend / couple / family
    private String budget;      // low / medium / high
    private int travelDays;
    private LocalDate startDate;
    private int startHour;
    private int endHour;
    private String arrivalAirport;
    private LocalTime arrivalTime;
    private int arrivalReadyMinutes;
    private String departureAirport;
    private LocalTime departureTime;
    private int departureLeadMinutes;
    private String intensity;   // relaxed / balanced / packed

    // ── 기존 숫자 입력 (슬롯 직접 설정 안 할 때 폴백용) ──
    private int dailyTourism;
    private int dailyMeal;
    private int dailyCafe;
    private int dailyBar;

    /**
     * 사용자가 직접 구성한 하루 슬롯 순서
     * 이 값이 있으면 dailyTourism/Meal/Cafe/Bar 는 무시
     *
     * 예: [
     *   {"type": "tourism"},
     *   {"type": "tourism"},
     *   {"type": "food",    "foodCategory": "한식"},
     *   {"type": "cafe"},
     *   {"type": "food",    "foodCategory": "양식"},
     *   {"type": "bar"}
     * ]
     */
    private List<SlotOrder> slotOrders;

    @Data
    public static class SlotOrder {
        private String type;          // tourism / food / cafe / bar
        private String foodCategory;  // 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 기타 (food일 때만)
        private int durationMinutes;  // 0이면 장소 유형에 따른 기본 체류 시간 사용
    }

    private List<String> preferredAreas;
    private List<String> mustVisit;

    /**
     * 사용자가 다른 화면(관광지/맛집 등)에서 미리 "저장"해 둔 장소 이름 목록.
     * mustVisit(사용자가 이 폼에 직접 타이핑한 꼭 가고 싶은 곳)과는 출처가 다르다.
     * 이 목록에 있는 장소는 점수 경쟁 없이 해당 유형 슬롯에 최우선으로 배치되고,
     * 남는 슬롯만 기존 추천 로직으로 채워진다.
     */
    private List<String> savedPlaceNames;

    private List<String> categories;
    private String styleDescription;

    /** 언어 설정 — "ko" / "en" (기본값 "ko") */
    private String lang;
}
