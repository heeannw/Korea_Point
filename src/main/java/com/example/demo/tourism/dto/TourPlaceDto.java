package com.example.demo.tourism.dto;

import java.util.List;

public class TourPlaceDto {
    private int id;
    private String title;
    private int type;
    private String addr;
    private Double lat;
    private Double lng;
    private String image;
    private String google_place_id;
    private String google_name;
    private String google_addr;
    private Double rating;
    private int user_ratings_total;
    private List<TourReviewDto> reviews;
    private Float score;
    private Float positive_ratio;
    private Double distance;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getGoogle_place_id() {
        return google_place_id;
    }

    public void setGoogle_place_id(String google_place_id) {
        this.google_place_id = google_place_id;
    }

    public String getGoogle_name() {
        return google_name;
    }

    public void setGoogle_name(String google_name) {
        this.google_name = google_name;
    }

    public String getGoogle_addr() {
        return google_addr;
    }

    public void setGoogle_addr(String google_addr) {
        this.google_addr = google_addr;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public int getUser_ratings_total() {
        return user_ratings_total;
    }

    public void setUser_ratings_total(int user_ratings_total) {
        this.user_ratings_total = user_ratings_total;
    }

    public List<TourReviewDto> getReviews() {
        return reviews;
    }

    public void setReviews(List<TourReviewDto> reviews) {
        this.reviews = reviews;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public Float getPositive_ratio() {
        return positive_ratio;
    }

    public void setPositive_ratio(Float positive_ratio) {
        this.positive_ratio = positive_ratio;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }
}
