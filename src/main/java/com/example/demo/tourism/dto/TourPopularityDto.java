package com.example.demo.tourism.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 한 관광지의 카테고리별 인기 순위다.
 * 한국관광공사 빅데이터 API가 열리면 그 응답을 이 형태로 옮겨 담는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourPopularityDto {

    /** 카테고리 안에서의 순위(1부터). */
    private Integer popularRank;
    /** 순위를 매긴 카테고리(TOURIST_ATTRACTION 등). 콘텐츠 유형과 1:1로 대응한다. */
    private String popularCategory;
    /** 원본 인기 지표. 순위가 같을 때 비교하거나 화면에 근거를 보여줄 때 쓴다. */
    private Double popularityScore;

    public TourPopularityDto() { }

    public TourPopularityDto(Integer popularRank, String popularCategory, Double popularityScore) {
        this.popularRank = popularRank;
        this.popularCategory = popularCategory;
        this.popularityScore = popularityScore;
    }

    public Integer getPopularRank() { return popularRank; }
    public void setPopularRank(Integer popularRank) { this.popularRank = popularRank; }

    public String getPopularCategory() { return popularCategory; }
    public void setPopularCategory(String popularCategory) { this.popularCategory = popularCategory; }

    public Double getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Double popularityScore) { this.popularityScore = popularityScore; }
}
