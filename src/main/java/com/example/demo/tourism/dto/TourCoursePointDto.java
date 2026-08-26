package com.example.demo.tourism.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 여행코스(콘텐츠 유형 25)를 구성하는 경유지 한 곳이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourCoursePointDto {

    private int order;
    private String name;
    private Double lat;
    private Double lng;

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
