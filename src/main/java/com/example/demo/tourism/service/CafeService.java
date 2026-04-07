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

    private static final double RADIUS_KM = 10.0; // 🔥 2 → 10으로 변경 (테스트용)

    private final ObjectMapper mapper = new ObjectMapper();

    private List<CafeDto> allCafes = new ArrayList<>();
    private Map<String, List<CafeDto.ReviewItem>> reviewMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            loadCafeScores();
            loadReviews();
            System.out.println("✅ CafeService 초기화 완료: " + allCafes.size() + "개 카페 로드");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCafeScores() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/cafe_scores.json");

        if (is == null) {
            throw new RuntimeException("❌ cafe_scores.json 없음");
        }

        allCafes = mapper.readValue(is, new TypeReference<List<CafeDto>>() {});
    }

    private void loadReviews() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/review_cafe.json");

        if (is == null) return;

        reviewMap = mapper.readValue(is,
                new TypeReference<Map<String, List<CafeDto.ReviewItem>>>() {});
    }

    // 🔥 문자열 정리 함수 (핵심)
    private String normalize(String s) {
        return s.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
    }

    // 🔥 검색 (핵심 수정)
    public Optional<CafeDto> searchByName(String keyword) {
        String k = normalize(keyword);

        return allCafes.stream()
                .filter(c -> {
                    String name = normalize(c.getName() == null ? "" : c.getName());
                    String gname = normalize(c.getGoogleName() == null ? "" : c.getGoogleName());

                    return name.contains(k) || gname.contains(k);
                })
                .findFirst();
    }

    // 🔥 키워드 기반 추천 (fallback 추가)
    public List<CafeDto> analyzeByKeyword(String keyword) {
        Optional<CafeDto> found = searchByName(keyword);

        if (found.isEmpty()) {
            System.out.println("❌ 검색 결과 없음 → 전체 반환");
            return allCafes; // 🔥 핵심
        }

        CafeDto target = found.get();
        return analyzeLocation(target.getLat(), target.getLng());
    }

    // 위치 기반 추천
    public List<CafeDto> analyzeLocation(Double lat, Double lng) {
        List<CafeDto> result = new ArrayList<>();

        for (CafeDto cafe : allCafes) {
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
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}