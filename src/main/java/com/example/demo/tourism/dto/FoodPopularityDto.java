package com.example.demo.tourism.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/** 음식점·카페 인기 공급자가 반환하는 순위 정보다. 장소 원본은 담지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FoodPopularityDto {

    private Integer popularRank;
    private String section;
    private Double popularityScore;
    private String popularityBasis;
    private LocalDateTime calculatedAt;

    public FoodPopularityDto() { }

    public FoodPopularityDto(Integer popularRank, String section, Double popularityScore,
                             String popularityBasis, LocalDateTime calculatedAt) {
        this.popularRank = popularRank;
        this.section = section;
        this.popularityScore = popularityScore;
        this.popularityBasis = popularityBasis;
        this.calculatedAt = calculatedAt;
    }

    public Integer getPopularRank() { return popularRank; }
    public void setPopularRank(Integer popularRank) { this.popularRank = popularRank; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Double getPopularityScore() { return popularityScore; }
    public void setPopularityScore(Double popularityScore) { this.popularityScore = popularityScore; }

    public String getPopularityBasis() { return popularityBasis; }
    public void setPopularityBasis(String popularityBasis) { this.popularityBasis = popularityBasis; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
}
