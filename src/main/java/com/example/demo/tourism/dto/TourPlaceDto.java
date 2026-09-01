package com.example.demo.tourism.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
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

    /** place_review 집계 (우리 사용자 리뷰). 리뷰 없으면 null / 0. */
    private Double userRating;
    private int    userReviewCount;

    private List<TourReviewDto> reviews;

    /** 여행코스일 때만 채워지는 경유지 목록이다. 다른 유형에서는 비어 있다. */
    private List<TourCoursePointDto> coursePoints = List.of();

    /** 음식점일 때 채워지는 음식 분류 슬러그(korean, cafe …). 화면에서 라벨로 옮긴다. */
    private String foodCategory;

    /** TourAPI 분류체계로 매긴 세부 타입명(해변. 해수욕장, 박물관 …). 없으면 null이다. */
    private String typeName;

    /** 항상 소분류 이름이다. 한식처럼 typeName이 중분류인 경우 상세에서 함께 보여준다. */
    private String typeDetailName;

    /** 카테고리 안에서의 인기 순위. 인기 데이터가 없으면 null이라 화면이 인기 표시를 감춘다. */
    private Integer popularRank;
    private String popularCategory;
    private Double popularityScore;
    /** 예약·사용자 행동·관광 빅데이터 등 순위 산정 근거. */
    private String popularityBasis;
    private LocalDateTime popularityCalculatedAt;

    /** 축제·행사(type 15)일 때만 채워지는 행사 기간(yyyyMMdd). 다른 유형은 null. */
    private String eventStartDate;
    private String eventEndDate;

    // 계산 필드
    private Float  score;
    private Double distance;

    public TourPlaceDto() { }

    /**
     * 얕은 복사. 목록 캐시가 요청마다 이 복사본을 넘겨, 순위·점수·거리 같은
     * 요청별 mutation이 캐시된 원본을 오염시키지 않게 한다.
     * (reviews·coursePoints 리스트는 캐시 후 변경되지 않아 참조만 공유한다)
     */
    // ponytail: 새 필드가 요청마다 값이 바뀌는 종류(순위·점수·거리 류)면 아래에도 추가할 것.
    //           단순 정적 필드면 빠뜨려도 무해하다.
    public TourPlaceDto(TourPlaceDto s) {
        this.id = s.id; this.title = s.title; this.type = s.type; this.addr = s.addr;
        this.lat = s.lat; this.lng = s.lng; this.image = s.image;
        this.google_place_id = s.google_place_id; this.google_name = s.google_name; this.google_addr = s.google_addr;
        this.rating = s.rating; this.user_ratings_total = s.user_ratings_total;
        this.userRating = s.userRating; this.userReviewCount = s.userReviewCount;
        this.reviews = s.reviews; this.coursePoints = s.coursePoints;
        this.foodCategory = s.foodCategory; this.typeName = s.typeName; this.typeDetailName = s.typeDetailName;
        this.popularRank = s.popularRank; this.popularCategory = s.popularCategory;
        this.popularityScore = s.popularityScore; this.popularityBasis = s.popularityBasis;
        this.popularityCalculatedAt = s.popularityCalculatedAt;
        this.eventStartDate = s.eventStartDate; this.eventEndDate = s.eventEndDate;
        this.score = s.score; this.distance = s.distance;
    }

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

    public Double getUserRating() { return userRating; }
    public void setUserRating(Double userRating) { this.userRating = userRating; }

    public int getUserReviewCount() { return userReviewCount; }
    public void setUserReviewCount(int userReviewCount) { this.userReviewCount = userReviewCount; }

    public List<TourReviewDto> getReviews() { return reviews; }
    public void setReviews(List<TourReviewDto> reviews) { this.reviews = reviews; }

    public List<TourCoursePointDto> getCoursePoints() { return coursePoints; }
    public void setCoursePoints(List<TourCoursePointDto> coursePoints) {
        this.coursePoints = coursePoints == null ? List.of() : coursePoints;
    }

    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public String getTypeDetailName() { return typeDetailName; }
    public void setTypeDetailName(String typeDetailName) { this.typeDetailName = typeDetailName; }

    public Integer getPopularRank() { return popularRank; }
    public void setPopularRank(Integer popularRank) { this.popularRank = popularRank; }

    public String getPopularCategory() { return popularCategory; }
    public void setPopularCategory(String popularCategory) { this.popularCategory = popularCategory; }

    public Double getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Double popularityScore) { this.popularityScore = popularityScore; }

    public String getPopularityBasis() { return popularityBasis; }
    public void setPopularityBasis(String popularityBasis) { this.popularityBasis = popularityBasis; }

    public LocalDateTime getPopularityCalculatedAt() { return popularityCalculatedAt; }
    public void setPopularityCalculatedAt(LocalDateTime popularityCalculatedAt) {
        this.popularityCalculatedAt = popularityCalculatedAt;
    }

    public Float getScore() { return score; }
    public void setScore(Float score) { this.score = score; }

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }

    public String getEventStartDate() { return eventStartDate; }
    public void setEventStartDate(String eventStartDate) { this.eventStartDate = eventStartDate; }

    public String getEventEndDate() { return eventEndDate; }
    public void setEventEndDate(String eventEndDate) { this.eventEndDate = eventEndDate; }
}
