package com.example.demo.trip.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trip_place")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 장소명 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 카테고리: tourism / food / cafe / bar */
    @Column(nullable = false, length = 20)
    private String category;

    /** 도로명 주소 */
    @Column(length = 300)
    private String address;

    /** 위도 */
    private Double lat;

    /** 경도 */
    private Double lng;

    /** 평균 평점 */
    private double avgRating;

    /** 리뷰 수 */
    private int reviewCount;

    /** 소개 설명 */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 대표 이미지 URL */
    @Column(length = 500)
    private String imageUrl;

    /** 영업시간 */
    @Column(length = 200)
    private String openHours;

    /** 전화번호 */
    @Column(length = 50)
    private String phone;

    /** 지역 태그 (예: 명동, 홍대) */
    @Column(length = 100)
    private String area;

    /** 기본 추천 점수 (JSON에서 로드) */
    private double baseScore;

    /** 외국인 커버리지 (model_lookup.json) */
    private double foreignCoverage;
}