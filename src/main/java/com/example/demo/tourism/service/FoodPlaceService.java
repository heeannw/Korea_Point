package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.example.demo.tourism.dto.FoodReviewDto;
import com.example.demo.tourism.entity.FoodPlace;
import com.example.demo.tourism.repository.FoodPlaceRepository;
import com.example.demo.tourism.repository.FoodRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodPlaceService {

    private final FoodRepository jsonRepository;
    private final FoodPlaceRepository jpaRepository;

    // ──────────────────────────────────────────────────────────────────────────
    // DB 초기화: 최초 1회 JSON → DB 적재
    // ──────────────────────────────────────────────────────────────────────────
    @PostConstruct
    public void init() throws Exception {

        if (jpaRepository.count() > 0) return;

        List<FoodPlace> all = new ArrayList<>();

        // ── 음식점 ──────────────────────────────────────────────────────────
        List<Map<String, Object>> foods = jsonRepository.loadFoodScores();

        for (Map<String, Object> f : foods) {
            try {
                all.add(FoodPlace.builder()
                        .name(str(f, "원본이름"))
                        .googleName(str(f, "구글이름"))
                        .lat(num(f, "위도"))
                        .lng(num(f, "경도"))
                        .avgStar(num(f, "avg_star"))
                        .reviewMean(num(f, "review_mean"))
                        .reviewCount((int) num(f, "review_count"))
                        .finalScore(num(f, "final_score"))
                        .category("RESTAURANT")
                        .build());
            } catch (Exception e) {
                // 개별 레코드 오류 무시하고 계속
            }
        }

        // ── 카페 ────────────────────────────────────────────────────────────
        List<Map<String, Object>> cafes = jsonRepository.loadCafeReviews();

        Map<String, List<Map<String, Object>>> grouped =
                cafes.stream()
                        .filter(r -> r.get("원본이름") != null)
                        .collect(Collectors.groupingBy(r -> (String) r.get("원본이름")));

        for (String name : grouped.keySet()) {
            try {
                Map<String, Object> first = grouped.get(name).get(0);
                double avgStar = num(first, "평균별점");

                all.add(FoodPlace.builder()
                        .name(name)
                        .googleName(str(first, "구글이름"))
                        .lat(num(first, "위도"))
                        .lng(num(first, "경도"))
                        .avgStar(avgStar)
                        .reviewMean(avgStar)
                        .reviewCount(grouped.get(name).size())
                        .finalScore(avgStar / 5.0)
                        .category("CAFE")
                        .build());
            } catch (Exception e) {
                // 개별 레코드 오류 무시하고 계속
            }
        }

        jpaRepository.saveAll(all);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 공개 API (컨트롤러용)
    // ──────────────────────────────────────────────────────────────────────────

    /** 전체 목록 → DTO 변환 (사용자 좌표 기준 거리 계산 포함) */
    public List<FoodPlaceDto> getAllAsDto(double userLat, double userLng) {
        return jpaRepository.findAll()
                .stream()
                .map(p -> toDto(p, userLat, userLng, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    /** 현위치 기반: 카테고리 필터 + 거리순 정렬 */
    public List<FoodPlaceDto> getNearbyAsDto(double userLat, double userLng, String category) {
        List<FoodPlace> places = "ALL".equals(category)
                ? jpaRepository.findAll()
                : jpaRepository.findByCategory(category);

        return places.stream()
                .map(p -> toDto(p, userLat, userLng, Collections.emptyList()))
                .sorted(Comparator.comparingDouble(FoodPlaceDto::getDistance))
                .collect(Collectors.toList());
    }

    /** 키워드 검색 + 카테고리 필터 */
    public List<FoodPlaceDto> searchAsDto(String keyword, String category,
                                          double userLat, double userLng) {
        List<FoodPlace> byName = jpaRepository.findByNameContaining(keyword);

        return byName.stream()
                .filter(p -> "ALL".equals(category) || category.equals(p.getCategory()))
                .map(p -> toDto(p, userLat, userLng, Collections.emptyList()))
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────────────────

    private FoodPlaceDto toDto(FoodPlace p, double userLat, double userLng,
                               List<FoodReviewDto> reviews) {
        return FoodPlaceDto.builder()
                .name(p.getName())
                .googleName(p.getGoogleName())
                .lat(p.getLat())
                .lng(p.getLng())
                .avgStar(p.getAvgStar())
                .reviewMean(p.getReviewMean())
                .reviewCount(p.getReviewCount())
                .finalScore(p.getFinalScore())
                .category(p.getCategory())
                .distance(haversine(userLat, userLng, p.getLat(), p.getLng()))
                .reviews(reviews)
                .build();
    }

    /** Haversine 공식으로 두 좌표 간 거리(km) 계산 */
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** null-safe 문자열 추출 */
    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    /** null-safe 숫자 추출 */
    private double num(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0.0;
        return ((Number) v).doubleValue();
    }
}