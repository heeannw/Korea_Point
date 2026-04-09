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

    private int dailyTourism;
    private int dailyMeal;
    private int dailyCafe;
    private int dailyBar;

    private List<String> preferredAreas;
    private List<String> mustVisit;
    private List<String> categories;
    private String styleDescription;
}