package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.CafeDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class CafeService {

    private static final double RADIUS_KM = 2.0;

    // ── 카페 판별 키워드 ───────────────────────────────────────────────────────
    private static final List<String> CAFE_INCLUDE = Arrays.asList(
            "카페", "coffee", "Coffee", "COFFEE", "커피", "cafe", "Cafe", "CAFE",
            "베이커리", "bakery", "Bakery", "roast", "Roast", "브런치", "디저트",
            "빵", "tea", "Tea", "도넛", "아티제", "블루보틀", "스타벅스", "이디야",
            "투썸", "할리스", "폴바셋", "빽다방", "컴포즈", "메가커피", "달콤커피"
    );

    private static final List<String> CAFE_EXCLUDE = Arrays.asList(
            "식당", "호프", "술집", "bar", "Bar", "BAR", "주점", "횟집", "고기",
            "삼겹", "치킨", "피자", "버거", "라멘", "라운지", "클럽", "나이트",
            "노래", "라이브", "스튜디오", "트램폴린", "병원", "학원", "쇼핑",
            "마트", "호텔", "모텔", "리조트", "스파", "헬스", "피트니스",
            "사무소", "공사"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    private List<CafeDto> allCafes   = new ArrayList<>();
    private List<CafeDto> cafeOnly   = new ArrayList<>();  // 카페만 필터링된 목록
    private Map<String, List<CafeDto.ReviewItem>> reviewMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            loadCafeScores();
            loadReviews();
            filterCafesOnly();
            System.out.println("✅ CafeService 초기화 완료: 전체 " + allCafes.size()
                    + "개 → 카페 " + cafeOnly.size() + "개 필터링");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCafeScores() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/cafe_scores.json");
        if (is == null) throw new RuntimeException("❌ cafe_scores.json 없음");
        allCafes = mapper.readValue(is, new TypeReference<List<CafeDto>>() {});
    }

    private void loadReviews() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/review_cafe.json");
        if (is == null) return;
        reviewMap = mapper.readValue(is,
                new TypeReference<Map<String, List<CafeDto.ReviewItem>>>() {});
    }

    // ── 카페 필터링 ───────────────────────────────────────────────────────────
    private void filterCafesOnly() {
        cafeOnly = new ArrayList<>();
        for (CafeDto cafe : allCafes) {
            if (isCafe(cafe)) cafeOnly.add(cafe);
        }
    }

    private boolean isCafe(CafeDto cafe) {
        String name = (cafe.getName() == null ? "" : cafe.getName())
                + " " + (cafe.getGoogleName() == null ? "" : cafe.getGoogleName());

        // 제외 키워드가 있으면 카페 아님
        for (String ex : CAFE_EXCLUDE) {
            if (name.contains(ex)) return false;
        }
        // 포함 키워드가 있으면 카페
        for (String inc : CAFE_INCLUDE) {
            if (name.contains(inc)) return true;
        }
        return false;
    }

    // ── 검색 ─────────────────────────────────────────────────────────────────
    private String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
    }

    public Optional<CafeDto> searchByName(String keyword) {
        String k = normalize(keyword);
        return cafeOnly.stream()
                .filter(c -> {
                    String name  = normalize(c.getName()       == null ? "" : c.getName());
                    String gname = normalize(c.getGoogleName() == null ? "" : c.getGoogleName());
                    return name.contains(k) || gname.contains(k);
                })
                .findFirst();
    }

    public List<CafeDto> analyzeByKeyword(String keyword) {
        Optional<CafeDto> found = searchByName(keyword);
        if (found.isEmpty()) {
            System.out.println("❌ 검색 결과 없음: " + keyword);
            return List.of();
        }
        CafeDto target = found.get();
        return analyzeLocation(target.getLat(), target.getLng());
    }

    // ── 위치 기반 추천 ────────────────────────────────────────────────────────
    public List<CafeDto> analyzeLocation(Double lat, Double lng) {
        List<CafeDto> result = new ArrayList<>();

        for (CafeDto cafe : cafeOnly) {
            if (cafe.getLat() == null || cafe.getLng() == null) continue;

            double dist = haversine(lat, lng, cafe.getLat(), cafe.getLng());
            if (dist > RADIUS_KM) continue;

            cafe.setDistance(Math.round(dist * 1000.0) / 1000.0);
            cafe.setReviews(reviewMap.getOrDefault(cafe.getName(), List.of()));
            result.add(cafe);
        }

        result.sort((a, b) -> Double.compare(
                b.getFinalScore() != null ? b.getFinalScore() : 0,
                a.getFinalScore() != null ? a.getFinalScore() : 0
        ));

        return result;
    }

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}