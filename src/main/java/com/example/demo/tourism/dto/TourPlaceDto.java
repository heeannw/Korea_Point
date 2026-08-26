package com.example.demo.tourism.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TourPlaceDto {

    // ✅ JSON에서 lat/lng가 String("37.496...")으로 오기 때문에 커스텀 역직렬화 필요
    private static class StringToDouble extends StdDeserializer<Double> {
        public StringToDouble() { super(Double.class); }
        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String v = p.getText();
            if (v == null || v.isBlank()) return null;
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
        }
    }

    private String id;
    private String title;
    private String type;
    private String addr;

    @JsonDeserialize(using = StringToDouble.class)
    private Double lat;

    @JsonDeserialize(using = StringToDouble.class)
    private Double lng;

    private String image;
    private String google_place_id;
    private String google_name;
    private String google_addr;
    private Double rating;
    private int    user_ratings_total;
    private List<TourReviewDto> reviews;

    /** 여행코스일 때만 채워지는 경유지 목록이다. 다른 유형에서는 비어 있다. */
    private List<TourCoursePointDto> coursePoints = List.of();

    /** 음식점일 때 채워지는 음식 분류 슬러그(korean, cafe …). 화면에서 라벨로 옮긴다. */
    private String foodCategory;

    // 계산 필드
    private Float  score;
    private Double distance;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getAddr() { return addr; }
    public void setAddr(String addr) { this.addr = addr; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getGoogle_place_id() { return google_place_id; }
    public void setGoogle_place_id(String google_place_id) { this.google_place_id = google_place_id; }

    public String getGoogle_name() { return google_name; }
    public void setGoogle_name(String google_name) { this.google_name = google_name; }

    public String getGoogle_addr() { return google_addr; }
    public void setGoogle_addr(String google_addr) { this.google_addr = google_addr; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public int getUser_ratings_total() { return user_ratings_total; }
    public void setUser_ratings_total(int user_ratings_total) { this.user_ratings_total = user_ratings_total; }

    public List<TourReviewDto> getReviews() { return reviews; }
    public void setReviews(List<TourReviewDto> reviews) { this.reviews = reviews; }

    public List<TourCoursePointDto> getCoursePoints() { return coursePoints; }
    public void setCoursePoints(List<TourCoursePointDto> coursePoints) {
        this.coursePoints = coursePoints == null ? List.of() : coursePoints;
    }

    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }

    public Float getScore() { return score; }
    public void setScore(Float score) { this.score = score; }

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
}