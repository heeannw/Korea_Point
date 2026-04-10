package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * FoodMapService
 *
 * [성능 개선]
 * 1. 목록 메서드(getAllPlaces / analyzeLocation / analyzeByKeyword)
 *    → reviews 필드를 포함하지 않음 (초기 응답 크기 대폭 감소)
 *
 * 2. getDetail(name) 신규
 *    → 상세 패널을 열 때만 해당 음식점 리뷰를 포함해 반환
 *       (프론트에서 fetch('/foodmap/detail/{name}') 로 지연 호출)
 *
 * 3. getPlacesJson(lat, lng, category) 신규
 *    → 카테고리 Ajax 전환용 JSON API (/foodmap/api/places) 에서 사용
 *
 * food_scores.json 카테고리: 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 바 / 기타
 */
@Service
public class FoodMapService {

    private static final double RADIUS_KM = 2.0;

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());

    /** food_scores.json → 메모리 */
    private List<FoodPlaceDto> restaurantsInMemory = new ArrayList<>();

    /** 음식점 리뷰 맵: 원본이름 → 리뷰 목록 */
    private Map<String, List<FoodPlaceDto.ReviewItem>> reviewMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            loadRestaurantJson();
            loadReviewJson();
            System.out.println("✅ FoodMapService 초기화 완료: 음식점 "
                    + restaurantsInMemory.size() + "개 / 리뷰 등록 업소 "
                    + reviewMap.size() + "개");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── JSON 로드 ────────────────────────────────────────────────────────────

    private void loadRestaurantJson() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/food/food_scores.json");
        if (is == null) {
            System.out.println("⚠ food_scores.json 없음 — 음식점 목록 비어 있음");
            return;
        }
        List<Map<String, Object>> raw = mapper.readValue(is, new TypeReference<>() {});
        for (Map<String, Object> m : raw) {
            FoodPlaceDto dto = new FoodPlaceDto();
            dto.setName(str(m, "name"));
            dto.setGoogleName(str(m, "googleName"));
            dto.setLat(dbl(m, "lat"));
            dto.setLng(dbl(m, "lng"));
            dto.setCategory(str(m, "category"));
            dto.setAvgStar(dbl(m, "avgStar"));
            dto.setFinalScore(dbl(m, "finalScore"));
            dto.setReviewCount(intVal(m, "reviewCount"));
            dto.setReviewMean(dbl(m, "reviewMean"));
            // ✅ reviews 는 여기서 세팅하지 않음 — 목록 응답에 불필요
            restaurantsInMemory.add(dto);
        }
    }

    private void loadReviewJson() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/food/reviews_food.json");
        if (is == null) return;

        List<Map<String, Object>> raw = mapper.readValue(is, new TypeReference<>() {});
        for (Map<String, Object> m : raw) {
            String name = str(m, "원본이름");
            if (name == null) continue;

            FoodPlaceDto.ReviewItem item = new FoodPlaceDto.ReviewItem();
            item.setRating(dblFromStr(m, "리뷰별점"));
            item.setContent(str(m, "리뷰내용"));

            reviewMap.computeIfAbsent(name, k -> new ArrayList<>()).add(item);
        }
    }

    // ── 전체 목록 반환 (반경 제한 없음, 리뷰 미포함) ──────────────────────────

    public List<FoodPlaceDto> getAllPlaces(String category) {
        List<FoodPlaceDto> out = new ArrayList<>();
        for (FoodPlaceDto r : restaurantsInMemory) {
            if (!"ALL".equals(category) && !category.equals(r.getCategory())) continue;
            FoodPlaceDto copy = copyDto(r);
            copy.setDistance(null);
            out.add(copy);
        }
        sortByScore(out);
        return out;
    }

    // ── 위치 기반 추천 (리뷰 미포함) ─────────────────────────────────────────

    public List<FoodPlaceDto> analyzeLocation(Double lat, Double lng, String category) {
        List<FoodPlaceDto> result = nearRestaurants(lat, lng, category);
        sortByScore(result);
        return result;
    }

    // ── 키워드 검색 (리뷰 미포함) ─────────────────────────────────────────────

    public List<FoodPlaceDto> analyzeByKeyword(String keyword, String category) {
        String k = normalize(keyword);
        Optional<FoodPlaceDto> hit = restaurantsInMemory.stream()
                .filter(r -> normalize(r.getName()).contains(k)
                        || normalize(r.getGoogleName() == null ? "" : r.getGoogleName()).contains(k))
                .findFirst();
        if (hit.isPresent()) {
            FoodPlaceDto found = hit.get();
            return analyzeLocation(found.getLat(), found.getLng(), category);
        }
        return List.of();
    }

    // ── Ajax 카테고리 전환용 (리뷰 미포함) ───────────────────────────────────

    /**
     * GET /foodmap/api/places?lat=&lng=&category= 에서 사용
     * lat/lng 이 null 이면 전체 반환, 있으면 2km 반경 필터
     */
    public List<FoodPlaceDto> getPlacesJson(Double lat, Double lng, String category) {
        if (lat == null || lng == null) {
            return getAllPlaces(category);
        }
        return analyzeLocation(lat, lng, category);
    }

    // ── 상세 조회 (리뷰 포함) ─────────────────────────────────────────────────

    /**
     * GET /foodmap/detail/{name} 에서 사용
     * 상세 패널을 열 때 한 번만 호출 → 리뷰 포함
     */
    public Optional<FoodPlaceDto> getDetail(String name) {
        String k = normalize(name);
        return restaurantsInMemory.stream()
                .filter(r -> normalize(r.getName()).equals(k)
                        || normalize(r.getName()).contains(k))
                .findFirst()
                .map(r -> {
                    FoodPlaceDto copy = copyDto(r);
                    copy.setReviews(reviewMap.getOrDefault(r.getName(), List.of()));
                    return copy;
                });
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private List<FoodPlaceDto> nearRestaurants(Double lat, Double lng, String category) {
        List<FoodPlaceDto> out = new ArrayList<>();
        for (FoodPlaceDto r : restaurantsInMemory) {
            if (r.getLat() == null || r.getLng() == null) continue;
            if (!"ALL".equals(category) && !category.equals(r.getCategory())) continue;

            double dist = haversine(lat, lng, r.getLat(), r.getLng());
            if (dist > RADIUS_KM) continue;

            FoodPlaceDto copy = copyDto(r);
            copy.setDistance(round3(dist));
            out.add(copy);
        }
        return out;
    }

    private void sortByScore(List<FoodPlaceDto> list) {
        list.sort((a, b) -> Double.compare(
                b.getFinalScore() != null ? b.getFinalScore() : 0,
                a.getFinalScore() != null ? a.getFinalScore() : 0
        ));
    }

    private FoodPlaceDto copyDto(FoodPlaceDto src) {
        FoodPlaceDto copy = new FoodPlaceDto();
        copy.setName(src.getName());
        copy.setGoogleName(src.getGoogleName());
        copy.setLat(src.getLat());
        copy.setLng(src.getLng());
        copy.setCategory(src.getCategory());
        copy.setAvgStar(src.getAvgStar());
        copy.setFinalScore(src.getFinalScore());
        copy.setReviewCount(src.getReviewCount());
        copy.setReviewMean(src.getReviewMean());
        return copy;
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private Double dbl(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        return ((Number) v).doubleValue();
    }

    private Double dblFromStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        return ((Number) v).intValue();
    }
}