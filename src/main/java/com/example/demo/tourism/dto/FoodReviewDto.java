package com.example.demo.tourism.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
    public class FoodReviewDto {
        private double rating;
        private String content;
    }

