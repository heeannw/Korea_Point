package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripRecommendRequest {

    private int age;
    private String companion;   // solo / friend / couple / family
    private String budget;      // low / medium / high
    private int travelDays;
    private int startHour;
    private int endHour;
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
    }

    private List<String> preferredAreas;
    private List<String> mustVisit;
    private List<String> categories;
    private String styleDescription;
}