package com.example.demo.tourism.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FoodPlaceDto {

    private String name;
    private String googleName;

    private double lat;
    private double lng;

    private double avgStar;
    private double reviewMean;
    private int reviewCount;

    private double finalScore;
    private double distance;

    private String category; // CAFE / RESTAURANT

    private List<FoodReviewDto> reviews;
}