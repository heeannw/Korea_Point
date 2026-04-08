package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoodPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String googleName;

    private double lat;
    private double lng;

    private double avgStar;
    private double reviewMean;
    private int    reviewCount;

    private double finalScore;

    private String category;   // CAFE / RESTAURANT
}