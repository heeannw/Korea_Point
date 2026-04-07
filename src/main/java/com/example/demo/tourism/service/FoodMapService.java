package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.example.demo.tourism.entity.FoodPlace;
import com.example.demo.tourism.repository.FoodPlaceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FoodMapService
 *
 * 카테고리:
 *   - "ALL"        : 카페 + 음식점 합산
 *   - "CAFE"       : 카페만 (cafe_scores.json 메모리 로드)
 *   - "RESTAURANT" : 음식점만 (FoodPlaceRepository DB)
 *
 * 카페는 CafeService 와 동일하게 JSON 파일을 @PostConstruct 로 로드합니다.
 * (DB 마이그레이션 없이 기존 방식 그대로 유지)
 */
@Service
public class FoodMapService {

    private static final double RADIUS_KM = 2.0;
    // 위경도 delta ≈ 2km / 111km
    private static final double DELTA     = 2.0 / 111.0;

    // ── 카페 필터 키워드 (CafeService 와 동일) ──────────────────────────────
    private static final List<String> CAFE_INCLUDE = Arrays.asList(
            "카페","coffee","Coffee","COFFEE","커피","cafe","Cafe","CAFE",
            "베이커리","bakery","Bakery","roast","Roast","브런치","디저트",
            "빵","tea","Tea","도넛","아티제","블루보틀","스타벅스","이디야",
            "투썸","할리스","폴바셋","빽다방","컴포즈","메가커피","달콤커피"
    );
    private static final List<String> CAFE_EXCLUDE = Arrays.asList(
            "식당","호프","술집","bar","Bar","BAR","주점","횟집","고기",
            "삼겹","치킨","피자","버거","라멘","라운지","클럽","나이트",
            "노래","라이브","스튜디오","트램폴린","병원","학원","쇼핑",
            "마트","호텔","모텔","리조트","스파","헬스","피트니스",
            "사무소","공사"
    );

    private final FoodPlaceRepository foodPlaceRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 카페 JSON → 메모리 (CafeDto 구조와 동일하게 FoodPlaceDto 로 변환) */
    private List<FoodPlaceDto> cafesInMemory = new ArrayList<>();
    /** 카페 리뷰 맵 */
    private Map<String, List<FoodPlaceDto.ReviewItem>> cafeReviewMap = new HashMap<>();

    public FoodMapService(FoodPlaceRepository foodPlaceRepository) {
        this.foodPlaceRepository = foodPlaceRepository;
    }

    @PostConstruct
    public void init() {
        try {
            loadCafeJson();
            loadCafeReviews();
            System.out.println("✅ FoodMapService 초기화 완료: 카페 " + cafesInMemory.size() + "개");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── JSON 로드 ────────────────────────────────────────────────────────────

    private void loadCafeJson() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/cafe_scores.json");
        if (is == null) { System.out.println("⚠ cafe_scores.json 없음 — 카페 목록 비어 있음"); return; }

        // JSON 구조: [{name, googleName, lat, lng, avgStar, finalScore, reviewCount, reviewMean}]
        List<Map<String, Object>> raw = mapper.readValue(is, new TypeReference<>() {});
        for (Map<String, Object> m : raw) {
            FoodPlaceDto dto = new FoodPlaceDto();
            dto.setName(str(m, "name"));
            dto.setGoogleName(str(m, "googleName"));
            dto.setLat(dbl(m, "lat"));
            dto.setLng(dbl(m, "lng"));
            dto.setAvgStar(dbl(m, "avgStar"));
            dto.setFinalScore(dbl(m, "finalScore"));
            dto.setReviewCount(intVal(m, "reviewCount"));
            dto.setReviewMean(dbl(m, "reviewMean"));
            dto.setCategory("CAFE");

            if (isCafe(dto)) cafesInMemory.add(dto);
        }
    }

    private void loadCafeReviews() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/review_cafe.json");
        if (is == null) return;

        Map<String, List<Map<String, Object>>> raw = mapper.readValue(is, new TypeReference<>() {});
        raw.forEach((cafeName, reviews) -> {
            List<FoodPlaceDto.ReviewItem> items = reviews.stream().map(r -> {
                FoodPlaceDto.ReviewItem item = new FoodPlaceDto.ReviewItem();
                item.setRating(dbl(r, "rating"));
                item.setContent(str(r, "content"));
                return item;
            }).collect(Collectors.toList());
            cafeReviewMap.put(cafeName, items);
        });
    }

    // ── 카페 필터 ────────────────────────────────────────────────────────────

    private boolean isCafe(FoodPlaceDto dto) {
        String name = (dto.getName() == null ? "" : dto.getName())
                + " " + (dto.getGoogleName() == null ? "" : dto.getGoogleName());
        for (String ex : CAFE_EXCLUDE) if (name.contains(ex)) return false;
        for (String inc : CAFE_INCLUDE) if (name.contains(inc)) return true;
        return false;
    }

    // ── 위치 기반 통합 추천 ──────────────────────────────────────────────────

    /**
     * @param category "ALL" | "CAFE" | "RESTAURANT"
     */
    public List<FoodPlaceDto> analyzeLocation(Double lat, Double lng, String category) {
        List<FoodPlaceDto> result = new ArrayList<>();

        if (!"RESTAURANT".equals(category)) {
            result.addAll(nearCafes(lat, lng));
        }
        if (!"CAFE".equals(category)) {
            result.addAll(nearRestaurants(lat, lng));
        }

        // finalScore 내림차순 정렬
        result.sort((a, b) -> Double.compare(
                b.getFinalScore() != null ? b.getFinalScore() : 0,
                a.getFinalScore() != null ? a.getFinalScore() : 0
        ));
        return result;
    }

    // ── 키워드 검색 ──────────────────────────────────────────────────────────

    /**
     * @param category "ALL" | "CAFE" | "RESTAURANT"
     */
    public List<FoodPlaceDto> analyzeByKeyword(String keyword, String category) {
        String k = normalize(keyword);

        // 카페에서 먼저 검색
        if (!"RESTAURANT".equals(category)) {
            Optional<FoodPlaceDto> cafeHit = cafesInMemory.stream()
                    .filter(c -> normalize(c.getName()).contains(k)
                            || normalize(c.getGoogleName() == null ? "" : c.getGoogleName()).contains(k))
                    .findFirst();
            if (cafeHit.isPresent()) {
                FoodPlaceDto hit = cafeHit.get();
                return analyzeLocation(hit.getLat(), hit.getLng(), category);
            }
        }

        // DB 음식점에서 검색
        if (!"CAFE".equals(category)) {
            List<FoodPlace> dbHits = foodPlaceRepository.findByNameContaining(k);
            if (!dbHits.isEmpty()) {
                FoodPlace hit = dbHits.get(0);
                return analyzeLocation(hit.getLat(), hit.getLng(), category);
            }
        }

        return List.of();
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private List<FoodPlaceDto> nearCafes(Double lat, Double lng) {
        List<FoodPlaceDto> out = new ArrayList<>();
        for (FoodPlaceDto cafe : cafesInMemory) {
            if (cafe.getLat() == null || cafe.getLng() == null) continue;
            double dist = haversine(lat, lng, cafe.getLat(), cafe.getLng());
            if (dist > RADIUS_KM) continue;
            cafe.setDistance(round3(dist));
            cafe.setReviews(cafeReviewMap.getOrDefault(cafe.getName(), List.of()));
            out.add(cafe);
        }
        return out;
    }

    private List<FoodPlaceDto> nearRestaurants(Double lat, Double lng) {
        List<FoodPlace> dbList = foodPlaceRepository.findNearby(lat, lng, DELTA);
        List<FoodPlaceDto> out = new ArrayList<>();
        for (FoodPlace fp : dbList) {
            if (fp.getLat() == null || fp.getLng() == null) continue;
            double dist = haversine(lat, lng, fp.getLat(), fp.getLng());
            if (dist > RADIUS_KM) continue;

            FoodPlaceDto dto = toDto(fp);
            dto.setDistance(round3(dist));
            out.add(dto);
        }
        return out;
    }

    private FoodPlaceDto toDto(FoodPlace fp) {
        FoodPlaceDto dto = new FoodPlaceDto();
        dto.setName(fp.getName());
        dto.setGoogleName(fp.getGoogleName());
        dto.setLat(fp.getLat());
        dto.setLng(fp.getLng());
        dto.setAvgStar(fp.getAvgStar());
        dto.setFinalScore(fp.getFinalScore());
        dto.setReviewCount(fp.getReviewCount());
        dto.setReviewMean(fp.getReviewMean());
        dto.setCategory("RESTAURANT");
        dto.setReviews(List.of());
        return dto;
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

    private Integer intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        return ((Number) v).intValue();
    }
}