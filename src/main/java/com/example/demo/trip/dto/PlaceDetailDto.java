package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class PlaceDetailDto {
    private Long   id;
    private String name;
    private String category;
    private String address;
    private Double lat;
    private Double lng;
    private double avgRating;
    private int    reviewCount;
    private String description;
    private String imageUrl;
    private String openHours;
    private String phone;
    private List<ReviewDto> reviews;

    @Data
    public static class ReviewDto {
        private String content;
        private double rating;
        private String author;
        private String date;
    }
}