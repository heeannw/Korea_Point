package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카페 엔티티 (cafe_scores.json 기반 데이터를 DB로도 관리할 경우 사용)
 *
 * ※ CafeService 는 현재 JSON 파일을 메모리 로드 방식으로 운영하므로,
 *   이 Entity 는 DB 연동이 필요할 때를 위한 대비용입니다.
 *   FoodMapService 에서 JSON 로드 방식을 그대로 사용하므로
 *   당장 DB 마이그레이션 없이도 동작합니다.
 */
@Entity
@Table(name = "cafe")
@Getter @Setter
@NoArgsConstructor
public class Cafe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String googleName;

    private Double lat;
    private Double lng;

    private Double avgStar;
    private Double finalScore;
    private Integer reviewCount;
    private Double reviewMean;
}