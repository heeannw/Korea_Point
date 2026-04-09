package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripRecommendRequest {

    /** 나이 */
    private int age;

    /** 동행 유형: friend / couple / family / solo */
    private String companion;

    /** 예산: low / medium / high */
    private String budget;

    /** 여행 일수 */
    private int travelDays;

    /** 여행 시작 시간 (시 단위, 예: 10) */
    private int startHour;

    /** 여행 종료 시간 (시 단위, 예: 21) */
    private int endHour;

    /** 여행 강도: relaxed / balanced / packed */
    private String intensity;

    /** 하루 관광 개수 */
    private int dailyTourism;

    /** 하루 식사 개수 */
    private int dailyMeal;

    /** 하루 카페 개수 */
    private int dailyCafe;

    /** 하루 바 개수 */
    private int dailyBar;

    /** 선호 지역 (예: ["명동", "홍대"]) */
    private List<String> preferredAreas;

    /** 꼭 가고 싶은 장소 (예: ["광장시장", "코엑스"]) */
    private List<String> mustVisit;

    /** 취향 카테고리 (예: ["local", "photo", "cafe"]) */
    private List<String> categories;

    /** 자유 텍스트 여행 스타일 설명 */
    private String styleDescription;
}