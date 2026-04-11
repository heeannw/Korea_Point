package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.CafeDto;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * CafeService
 * 카페 데이터를 관리하고 키워드 필터링, 검색, 위치 기반 추천 로직을 수행합니다.
 *
 * [수정 내용]
 * cafe_scores.json 은 AI 모델이 이미 카페로 판별한 결과물이므로,
 * 기존의 INCLUDE 키워드 방식(이름에 '카페', '커피' 등이 없으면 탈락)을 제거합니다.
 * EXCLUDE 키워드만 남겨 치킨집·노래방·식당 등 명백한 비카페만 걸러냅니다.
 * 결과: 336개 → 약 1,116개로 복원
 */
@Service
public class CafeService {

    private static final double RADIUS_KM = 2.0;

    /**
     * 이름에 포함되어 있으면 카페에서 제외할 키워드들
     *
     * [수정] 기존 CAFE_INCLUDE 리스트 제거
     *   → cafe_scores.json 자체가 AI 모델이 카페로 판별한 데이터이므로
     *     INCLUDE 재필터링은 불필요하며, 이름이 독특한 카페(영문명, 브랜딩명 등)를
     *     대거 누락시키는 원인이었습니다.
     *
     * [수정] CAFE_EXCLUDE에서 오탐률 높은 키워드 제거
     *   제거 항목: bar/Bar/BAR (Lit coffee bar 등 카페도 걸림),
     *              라운지 (카페 라운지 등 걸림), 라이브 (라이브카페 걸림),
     *              스튜디오 (포토스튜디오 카페 걸림), 병원 (아티제 병원점 걸림),
     *              쇼핑 (쇼핑몰 내 카페 걸림), 호텔 (호텔 카페 걸림),
     *              스파 (스파 카페 걸림)
     */
    private static final List<String> CAFE_EXCLUDE = Arrays.asList(
            "식당", "호프", "술집", "주점", "횟집", "고기",
            "삼겹", "치킨", "피자", "버거", "라멘", "클럽", "나이트",
            "노래", "트램폴린", "학원",
            "마트", "모텔", "리조트", "헬스", "피트니스",
            "사무소", "공사"
    );

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());

    private List<CafeDto> allCafes = new ArrayList<>(); // 전체 장소 데이터
    private List<CafeDto> cafeOnly = new ArrayList<>(); // 카페로 필터링된 데이터
    private Map<String, List<CafeDto.ReviewItem>> reviewMap = new HashMap<>(); // 장소별 리뷰 맵

    /**
     * 의존성 주입 후 초기화 메서드
     * 데이터를 로드하고 리뷰 매핑 및 카페 필터링을 수행합니다.
     */
    @PostConstruct
    public void init() {
        try {
            loadCafeScores();   // 1. 장소 점수 데이터 로드
            loadReviews();      // 2. 리뷰 데이터 로드 및 매핑
            filterCafesOnly();  // 3. 명백한 비카페만 제외 (EXCLUDE only)
            loadEnNames();      // 4. ✅ 영어 이름 매핑 (파파고 호출 대체)
            System.out.println("✅ CafeService 초기화 완료: 전체 " + allCafes.size()
                    + "개 → 카페 " + cafeOnly.size() + "개 필터링");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── [1] 데이터 로딩 로직 ─────────────────────────────────────────────────

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

        List<Map<String, Object>> raw = mapper.readValue(is, new TypeReference<>() {});
        for (Map<String, Object> m : raw) {
            String name = str(m, "원본이름");
            if (name == null) continue;

            CafeDto.ReviewItem item = new CafeDto.ReviewItem();
            item.setRating(dblFromObj(m, "리뷰별점"));
            item.setContent(str(m, "리뷰내용"));

            reviewMap.computeIfAbsent(name, k -> new ArrayList<>()).add(item);
        }
    }

    private void loadEnNames() throws IOException {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/cafe/cafe_scores_EN.json");
        if (is == null) { System.out.println("⚠️ cafe_scores_EN.json 없음"); return; }

        List<Map<String, Object>> enList = mapper.readValue(is, new TypeReference<>() {});
        Map<String, String> nameEnMap     = new HashMap<>();
        Map<String, String> categoryEnMap = new HashMap<>();
        for (Map<String, Object> m : enList) {
            String name = str(m, "name");
            String en   = str(m, "nameEn");
            String cat  = str(m, "categoryEn");
            if (name != null && en  != null) nameEnMap.put(name, en);
            if (name != null && cat != null) categoryEnMap.put(name, cat);
        }
        for (CafeDto cafe : cafeOnly) {
            if (cafe.getName() != null) {
                cafe.setNameEn(nameEnMap.get(cafe.getName()));
                cafe.setCategoryEn(categoryEnMap.get(cafe.getName()));
            }
        }
        System.out.println("✅ 카페 영어 이름 매핑 완료: " + nameEnMap.size() + "개");
    }

    // ── [2] 카페 필터링 ───────────────────────────────────────────────────────

    /**
     * [수정] EXCLUDE 키워드에 해당하는 명백한 비카페만 제거합니다.
     * cafe_scores.json 은 AI 모델이 이미 카페로 판별한 데이터이므로
     * INCLUDE 재필터링 없이 EXCLUDE만 적용합니다.
     */
    private void filterCafesOnly() {
        cafeOnly = new ArrayList<>();
        for (CafeDto cafe : allCafes) {
            if (!isExcluded(cafe)) {
                cafeOnly.add(cafe);
            }
        }
    }

    /**
     * EXCLUDE 키워드가 이름/구글명에 포함되면 true (카페 목록에서 제외)
     */
    private boolean isExcluded(CafeDto cafe) {
        String name = (cafe.getName() == null ? "" : cafe.getName())
                + " " + (cafe.getGoogleName() == null ? "" : cafe.getGoogleName());
        for (String ex : CAFE_EXCLUDE) {
            if (name.contains(ex)) return true;
        }
        return false;
    }

    // ── [3] 전체 목록 반환 (반경 제한 없음) ──────────────────────────────────

    /**
     * /cafe/all 용 — 2km 반경 제한 없이 전체 카페 반환
     */
    public List<CafeDto> getAllCafes() {
        List<CafeDto> result = new ArrayList<>();
        for (CafeDto cafe : cafeOnly) {
            CafeDto copy = copyDto(cafe);
            copy.setDistance(null);
            copy.setReviews(reviewMap.getOrDefault(cafe.getName(), List.of()));
            result.add(copy);
        }
        result.sort((a, b) -> Double.compare(
                b.getFinalScore() != null ? b.getFinalScore() : 0,
                a.getFinalScore() != null ? a.getFinalScore() : 0
        ));
        return result;
    }

    // ── [4] 검색 ─────────────────────────────────────────────────────────────

    private String normalize(String s) {
        return s == null ? "" : s.replaceAll("[^가-힣a-zA-Z0-9]", "").toLowerCase();
    }

    public Optional<CafeDto> searchByName(String keyword) {
        String k = normalize(keyword);
        return cafeOnly.stream()
                .filter(c -> {
                    String name  = normalize(c.getName());
                    String gname = normalize(c.getGoogleName());
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

    // ── [5] 위치 기반 추천 ────────────────────────────────────────────────────

    public List<CafeDto> analyzeLocation(Double lat, Double lng) {
        List<CafeDto> result = new ArrayList<>();

        for (CafeDto cafe : cafeOnly) {
            if (cafe.getLat() == null || cafe.getLng() == null) continue;

            double dist = haversine(lat, lng, cafe.getLat(), cafe.getLng());
            if (dist > RADIUS_KM) continue;

            CafeDto copy = copyDto(cafe);
            copy.setDistance(Math.round(dist * 1000.0) / 1000.0);
            copy.setReviews(reviewMap.getOrDefault(cafe.getName(), List.of()));
            result.add(copy);
        }

        result.sort((a, b) -> Double.compare(
                b.getFinalScore() != null ? b.getFinalScore() : 0,
                a.getFinalScore() != null ? a.getFinalScore() : 0
        ));

        System.out.println("전체 후보 수: " + cafeOnly.size());
        System.out.println("필터 후 결과 수: " + result.size());

        return result;
    }

    // ── [6] 유틸리티 메서드 ──────────────────────────────────────────────────

    private CafeDto copyDto(CafeDto src) {
        CafeDto copy = new CafeDto();
        copy.setName(src.getName());
        copy.setGoogleName(src.getGoogleName());
        copy.setLat(src.getLat());
        copy.setLng(src.getLng());
        copy.setAvgStar(src.getAvgStar());
        copy.setFinalScore(src.getFinalScore());
        copy.setReviewCount(src.getReviewCount());
        copy.setReviewMean(src.getReviewMean());
        copy.setNameEn(src.getNameEn());         // ✅ 영어 이름
        copy.setCategoryEn(src.getCategoryEn()); // ✅ 영어 카테고리
        return copy;
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

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private Double dblFromObj(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return null; }
    }
}