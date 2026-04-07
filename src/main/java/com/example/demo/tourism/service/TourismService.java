package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.dto.TourReviewDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class TourismService {

    // ── 가중치 ───────────────────────────────────────────────
    private static final float W_RATING     = 0.30f;
    private static final float W_SENTIMENT  = 0.28f;
    private static final float W_POPULARITY = 0.18f;
    private static final float W_FOREIGN    = 0.12f;
    private static final float W_COVERAGE   = 0.08f;
    private static final float W_AGE_POP    = 0.04f;

    // ── 카테고리 보너스 ──────────────────────────────────────
    private static final Map<String, Float> CATEGORY_BONUS = new HashMap<>() {{
        put("25", 0.10f);
        put("38", 0.08f);
        put("15", 0.07f);
        put("12", 0.04f);
        put("14", 0.02f);
        put("28", 0.00f);
    }};

    // ── 정규화 기준값 ────────────────────────────────────────
    private static final float RATING_MIN    = 1.0f;
    private static final float RATING_MAX    = 5.0f;
    private static final float LOG_TOTAL_MAX = (float) Math.log1p(70_000);
    private static final float POS_RATIO_MAX = 0.25f;
    private static final int   BAYESIAN_C    = 100;

    // ── 외국인 긍정어 ────────────────────────────────────────
    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "amazing","beautiful","great","wonderful","nice","love","best",
            "awesome","fantastic","perfect","excellent","good","peaceful",
            "clean","fun","interesting","impressive","recommend","must","enjoy",
            "enjoyed","worth","highly","definitely","friendly","pretty",
            "stunning","lovely","delightful","remarkable","breathtaking",
            "charming","magnificent","superb","splendid","well","easy",
            "free","traditional","unique","special","view"
    ));

    // ── 외국어 코드 ──────────────────────────────────────────
    private static final Set<String> FOREIGN_LANGS = new HashSet<>(Arrays.asList(
            "en","zh","zh-Hant","zh-Hans","ja","fr","de","es","th","ru","id"
    ));

    // ── 룩업테이블 ───────────────────────────────────────────
    private double globalAvgRating = 4.47;
    private double agePopMax       = 74.5;
    private final Map<String, Double> foreignCoverage = new HashMap<>();
    private final Map<String, Double> agePopularity   = new HashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────
    // 초기화
    // ─────────────────────────────────────────────────────────
    @PostConstruct
    public void init() {
        try {
            loadLookup();
            System.out.println("✅ TourismService 초기화 완료");
            System.out.println("   globalAvgRating=" + globalAvgRating
                    + " agePopMax=" + agePopMax
                    + " coverage=" + foreignCoverage.size() + "개"
                    + " agePop=" + agePopularity.size() + "개");
        } catch (Exception e) {
            System.out.println("⚠️ TourismService 로드 실패 (기본값 사용)");
            e.printStackTrace();
        }
    }

    private void loadLookup() throws Exception {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("model/tourism/model_lookup.json");
        if (is == null) throw new RuntimeException("model_lookup.json 없음");

        JsonNode root = mapper.readTree(is);
        globalAvgRating = root.get("global_avg_rating").asDouble();
        agePopMax       = root.get("age_pop_max").asDouble();

        root.get("foreign_coverage").fields()
                .forEachRemaining(e -> foreignCoverage.put(e.getKey(), e.getValue().asDouble()));
        root.get("age_popularity").fields()
                .forEachRemaining(e -> agePopularity.put(e.getKey(), e.getValue().asDouble()));
    }

    // ─────────────────────────────────────────────────────────
    // 점수 계산 (0~100)
    // ─────────────────────────────────────────────────────────
    public double score(String title, int typeCode, double rating,
                        int userRatingsTotal, List<TourReviewDto> reviews) {

        if (reviews == null) reviews = List.of(); // ✅ NPE 방지

        double[] feat = extractFeatures(title, rating, userRatingsTotal, reviews);
        double effectiveRating  = feat[0];
        double foreignRatio     = feat[1];
        double foreignSentiment = feat[2];
        double coverage         = feat[3];
        double agePop           = feat[4];

        double bay = bayesianRating(effectiveRating, userRatingsTotal);

        float bonus = CATEGORY_BONUS.getOrDefault(String.valueOf(typeCode), 0.0f); // ✅ int → String 변환

        double base = W_RATING     * normRating(bay)
                + W_SENTIMENT  * normSentiment(foreignSentiment)
                + W_POPULARITY * normLogTotal(userRatingsTotal)
                + W_FOREIGN    * Math.min(foreignRatio, 1.0)
                + W_COVERAGE   * Math.min(coverage, 1.0)
                + W_AGE_POP    * normAgePop(agePop);

        return Math.min(base * (1 + bonus) * 100.0, 100.0);
    }

    // ─────────────────────────────────────────────────────────
    // 현위치 기반 2km 이내 추천 ✅ 핵심 메서드
    // ─────────────────────────────────────────────────────────
    public List<TourPlaceDto> analyzeLocation(Double lat, Double lon) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("data/tourism/seoul_recommend_candidates.json"); // 경로 수정
            if (is == null) throw new RuntimeException("JSON 파일 없음 - 경로 확인 필요");

            List<TourPlaceDto> list = mapper.readValue(is, new TypeReference<>() {});
            List<TourPlaceDto> nearbyList = new ArrayList<>();

            for (TourPlaceDto obj : list) {
                if (obj.getLat() == null || obj.getLng() == null) continue;

                double distance = haversine(lat, lon, obj.getLat(), obj.getLng());
                if (distance > 2.0) continue;

                double s = score(
                        obj.getTitle(),
                        obj.getType(),
                        obj.getRating() != null ? obj.getRating() : 0.0, //
                        obj.getUser_ratings_total(),
                        obj.getReviews() // score() 안에서 null 처리함
                );
                obj.setScore((float) s);
                obj.setDistance(Math.round(distance * 1000.0) / 1000.0);
                nearbyList.add(obj);
            }

            // ✅ 점수 높은 순 정렬
            nearbyList.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
            return nearbyList;

        } catch (IOException e) {
            throw new RuntimeException("JSON 읽기 실패: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // 피처 추출
    // ─────────────────────────────────────────────────────────
    private double[] extractFeatures(String title, double rating,
                                     int total, List<TourReviewDto> reviews) {
        List<TourReviewDto> foreignReviews = new ArrayList<>();
        for (TourReviewDto r : reviews) {
            if (FOREIGN_LANGS.contains(r.getLanguage()))
                foreignReviews.add(r);
        }

        double foreignRatio = reviews.isEmpty() ? 0.0
                : (double) foreignReviews.size() / reviews.size();

        StringBuilder sb = new StringBuilder();
        for (TourReviewDto r : foreignReviews)
            sb.append(" ").append(r.getText() != null ? r.getText().toLowerCase() : "");
        String text = sb.toString();
        String[] words = text.trim().split("\\s+");
        int wordCount = Math.max(words.length, 1);
        long posHits = POSITIVE_WORDS.stream().filter(text::contains).count();
        double foreignSentiment = foreignReviews.isEmpty() ? 0.0 : (double) posHits / wordCount;

        double effectiveRating = rating;
        if (!foreignReviews.isEmpty()) {
            double sum = 0; int cnt = 0;
            for (TourReviewDto r : foreignReviews) {
                Double rv = r.getRating();
                if (rv != null) { sum += rv; cnt++; }
            }
            if (cnt > 0) effectiveRating = sum / cnt;
        }

        double coverage = lookupWithPartial(foreignCoverage, title);
        double agePop   = lookupWithPartial(agePopularity, title);

        return new double[]{effectiveRating, foreignRatio, foreignSentiment, coverage, agePop};
    }

    // ─────────────────────────────────────────────────────────
    // 정규화 & Bayesian
    // ─────────────────────────────────────────────────────────
    private double bayesianRating(double rating, int total) {
        return (BAYESIAN_C * globalAvgRating + total * rating) / (BAYESIAN_C + total);
    }
    private double normRating(double v) {
        return (v - RATING_MIN) / (RATING_MAX - RATING_MIN);
    }
    private double normLogTotal(int total) {
        return Math.min(Math.log1p(total) / LOG_TOTAL_MAX, 1.0);
    }
    private double normSentiment(double v) {
        return Math.min(v / POS_RATIO_MAX, 1.0);
    }
    private double normAgePop(double v) {
        return Math.min(v / agePopMax, 1.0);
    }

    // ─────────────────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────────────────
    private double lookupWithPartial(Map<String, Double> map, String title) {
        if (map.containsKey(title)) return map.get(title);
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (e.getKey().contains(title) || title.contains(e.getKey()))
                return e.getValue();
        }
        return 0.0;
    }

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371.0;
        double phi1 = Math.toRadians(lat1), phi2 = Math.toRadians(lat2);
        double dphi = Math.toRadians(lat2 - lat1);
        double dlam = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dphi / 2) * Math.sin(dphi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(dlam / 2) * Math.sin(dlam / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
    //검색어 들어왓을떄 위치 찾기
    public List<TourPlaceDto> searchKeyword(String keyword) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("data/tourism/seoul_recommend_candidates.json");
        if (is == null) throw new RuntimeException("JSON 파일 없음");

        try {
            List<TourPlaceDto> list = mapper.readValue(is, new TypeReference<List<TourPlaceDto>>() {});

            //위치 좌표 찾기
            List<TourPlaceDto> resultList = new ArrayList<>();
            for (TourPlaceDto a : list) {
                String title = a.getTitle() != null ? a.getTitle() : "";
                String gName = a.getGoogle_name() != null ? a.getGoogle_name() : "";
                if (title.contains(keyword) || gName.contains(keyword)) {
                    resultList.add(a);
                    break; // 찾으면 break
                }
            }



//            System.out.println("lat!! " + lat);
//            System.out.println("lon!! " + lon);
//            // ✅ 못 찾으면 빈 리스트 반환
//            if (lat == null || lon == null) return new ArrayList<>();
//
//            // ✅ 2루프: 찾은 좌표 기준으로 2km 이내 필터링
////            List<TourPlaceDto> nearbyList = new ArrayList<>();
//
//            System.out.println(analyzeLocation(lat,lon).get(0).getRating());
//            System.out.println(analyzeLocation(lat,lon).get(0).getTitle());
//
            return resultList;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}