package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.dto.TourReviewDto;
import com.example.demo.tourism.dto.TourismDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 서울 외국인 관광지 추천 ML 서비스
 * TransportMlService와 동일한 패턴으로 구현
 *
 * 필요 리소스 (src/main/resources/model/tourism/ 에 배치):
 *   - model_lookup.json   ← Python fit() 결과 (룩업테이블)
 */
@Service
public class TourismService {

    // ── 가중치 (new.py와 동일) ───────────────────────────────
    private static final float W_RATING     = 0.30f;
    private static final float W_SENTIMENT  = 0.28f;
    private static final float W_POPULARITY = 0.18f;
    private static final float W_FOREIGN    = 0.12f;
    private static final float W_COVERAGE   = 0.08f;
    private static final float W_AGE_POP    = 0.04f;

    // ── 카테고리 보너스 ──────────────────────────────────────
    private static final Map<String, Float> CATEGORY_BONUS = new HashMap<>() {{
        put("25", 0.10f);  // 체험관광 84%
        put("38", 0.08f);  // 시장/쇼핑 76%
        put("15", 0.07f);  // 축제/행사 74%
        put("12", 0.04f);  // 문화시설  65%
        put("14", 0.02f);  // 박물관/전시 59%
        put("28", 0.00f);  // 자연/야외  57%
    }};

    // ── 정규화 기준값 ────────────────────────────────────────
    private static final float RATING_MIN      = 1.0f;
    private static final float RATING_MAX      = 5.0f;
    private static final float LOG_TOTAL_MAX   = (float) Math.log1p(70_000);
    private static final float POS_RATIO_MAX   = 0.25f;
    private static final int   BAYESIAN_C      = 100;

    // ── 외국인 긍정어 (new.py POSITIVE_WORDS_EN) ────────────
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

    // ── fit() 결과 룩업테이블 (model_lookup.json에서 로드) ──
    private double globalAvgRating = 4.47;
    private double agePopMax       = 74.5;
    private final Map<String, Double> foreignCoverage  = new HashMap<>();
    private final Map<String, Double> agePopularity    = new HashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────
    // 초기화 (TransportMlService.init()과 동일 패턴)
    // ─────────────────────────────────────────────────────────
    @PostConstruct
    public void init() {
        try {
            loadLookup();
            System.out.println("✅ TourMlService 초기화 완료");
            System.out.println("   globalAvgRating=" + globalAvgRating
                    + " agePopMax=" + agePopMax
                    + " coverage=" + foreignCoverage.size() + "개"
                    + " agePop=" + agePopularity.size() + "개");
        } catch (Exception e) {
            System.out.println("⚠️ TourMlService 로드 실패 (기본값 사용)");
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
    // 핵심 API: 관광지 점수 계산 (0~100)
    // ─────────────────────────────────────────────────────────

    /**
     * 단일 관광지 점수 계산
     *
     * @param title              관광지명
     * @param typeCode           관광지 유형 코드 ("12","14","15","25","28","38")
     * @param rating             구글 평점 (0~5)
     * @param userRatingsTotal   리뷰 수
     * @param reviews            리뷰 목록 (각 Map에 "text", "language" 키)
     */
    public double score(String title, String typeCode, double rating,
                        int userRatingsTotal, List<TourReviewDto> reviews) {

        // 1) 피처 추출
        double[] feat = extractFeatures(title, rating, userRatingsTotal, reviews);
        double effectiveRating  = feat[0];
        double foreignRatio     = feat[1];
        double foreignSentiment = feat[2];
        double coverage         = feat[3];
        double agePop           = feat[4];

        // 2) Bayesian 평점
        double bay = bayesianRating(effectiveRating, userRatingsTotal);

        // 3) 카테고리 보너스
        float bonus = CATEGORY_BONUS.getOrDefault(typeCode, 0.0f);

        // 4) 가중 합산
        double base = W_RATING     * normRating(bay)
                + W_SENTIMENT  * normSentiment(foreignSentiment)
                + W_POPULARITY * normLogTotal(userRatingsTotal)
                + W_FOREIGN    * Math.min(foreignRatio, 1.0)
                + W_COVERAGE   * Math.min(coverage, 1.0)
                + W_AGE_POP    * normAgePop(agePop);

        return Math.min(base * (1 + bonus) * 100.0, 100.0);
    }

    /**
     * 현위치 기반 반경 내 관광지 Top-N 추천
     *
     * @param candidates  전체 후보 리스트 (각 Map에 title, type, lat, lng, rating,
     *                    user_ratings_total, reviews 키 포함)
     * @param userLat     사용자 위도
     * @param userLng     사용자 경도
     * @param radiusKm    반경 (km)
     * @param topN        반환할 최대 개수
     */
    public List<Map<String, Object>> recommend(
            List<Map<String, Object>> candidates,
            double userLat, double userLng,
            double radiusKm, int topN) {

        List<Map<String, Object>> results = new ArrayList<>();

        for (Map<String, Object> place : candidates) {
            double lat = toDouble(place.get("lat"));
            double lng = toDouble(place.get("lng"));
            double dist = haversine(userLat, userLng, lat, lng);

            if (dist > radiusKm) continue;

            String title = (String) place.get("title");
            String typeCode = String.valueOf(place.get("type"));
            double rating = toDouble(place.getOrDefault("rating", 0.0));
            int total  = toInt(place.getOrDefault("user_ratings_total", 0));

            @SuppressWarnings("unchecked")
            List<TourReviewDto> reviews =
                    (List<TourReviewDto>) place.getOrDefault("reviews", List.of());

            double s = score(title, typeCode, rating, total, reviews);

            Map<String, Object> result = new LinkedHashMap<>(place);
            result.put("new_score",   Math.round(s * 10000.0) / 10000.0);
            result.put("distance_km", Math.round(dist * 1000.0) / 1000.0);
            results.add(result);
        }

        results.sort((a, b) -> Double.compare(
                (double) b.get("new_score"),
                (double) a.get("new_score")));

        return results.subList(0, Math.min(topN, results.size()));
    }

    /**
     * 카테고리별 Top-N 추천
     */
    public Map<String, List<Map<String, Object>>> recommendByCategory(
            List<Map<String, Object>> candidates,
            double userLat, double userLng,
            double radiusKm, int topNPerCategory) {

        Map<String, List<Map<String, Object>>> byType = new LinkedHashMap<>();

        for (Map<String, Object> place : candidates) {
            double lat = toDouble(place.get("lat"));
            double lng = toDouble(place.get("lng"));
            if (haversine(userLat, userLng, lat, lng) > radiusKm) continue;

            String title    = (String) place.get("title");
            String typeCode = String.valueOf(place.get("type"));
            double rating   = toDouble(place.getOrDefault("rating", 0.0));
            int    total    = toInt(place.getOrDefault("user_ratings_total", 0));

            @SuppressWarnings("unchecked")
            List<TourReviewDto> reviews =
                    (List<TourReviewDto>) place.getOrDefault("reviews", List.of());

            double s = score(title, typeCode, rating, total, reviews);

            Map<String, Object> result = new LinkedHashMap<>(place);
            result.put("new_score", Math.round(s * 10000.0) / 10000.0);

            byType.computeIfAbsent(typeCode, k -> new ArrayList<>()).add(result);
        }

        Map<String, String> typeLabels = Map.of(
                "12","문화시설", "14","박물관/전시", "15","축제/행사",
                "25","체험관광", "28","자연/야외",   "38","시장/쇼핑"
        );

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        byType.forEach((tc, places) -> {
            places.sort((a, b) -> Double.compare(
                    (double) b.get("new_score"), (double) a.get("new_score")));
            String label = typeLabels.getOrDefault(tc, "기타(" + tc + ")");
            result.put(label, places.subList(0, Math.min(topNPerCategory, places.size())));
        });

        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 내부 피처 추출
    // ─────────────────────────────────────────────────────────
    private double[] extractFeatures(String title, double rating,
                                     int total, List<TourReviewDto> reviews) {
        // 외국인 리뷰 분리
        List<TourReviewDto> foreignReviews = new ArrayList<>();
        for (TourReviewDto r : reviews) {
            if (FOREIGN_LANGS.contains(r.getLanguage()))
                foreignReviews.add(r);
        }

        // 외국인 리뷰 비율
        double foreignRatio = reviews.isEmpty() ? 0.0
                : (double) foreignReviews.size() / reviews.size();

        // 외국인 긍정어 비율
        StringBuilder sb = new StringBuilder();
        for (TourReviewDto r : foreignReviews)
            sb.append(" ").append(r.getText().toLowerCase());
        String text = sb.toString();
        String[] words = text.trim().split("\\s+");
        int wordCount = Math.max(words.length, 1);
        long posHits = POSITIVE_WORDS.stream().filter(text::contains).count();
        double foreignSentiment = foreignReviews.isEmpty() ? 0.0 : (double) posHits / wordCount;

        // 외국인 리뷰 평균 평점 (없으면 전체 평점)
        double effectiveRating = rating;
        if (!foreignReviews.isEmpty()) {
            double sum = 0; int cnt = 0;
            for (TourReviewDto r : foreignReviews) {
                Double rv = r.getRating();
                if (rv != null) {
                    sum += rv;
                    cnt++;
                }
            }
            if (cnt > 0) effectiveRating = sum / cnt;
        }

        // 커버리지 룩업 (부분 매칭 포함)
        double coverage = lookupWithPartial(foreignCoverage, title);

        // 세대별 인기도 룩업
        double agePop = lookupWithPartial(agePopularity, title);

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

    public void analyzeLocation(Double lat, Double lon) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            File file = new File("src/resources/data/tourism/seoul_recommend_.json");
            //json파일 읽어서 플레이스 객체로 변환해서 담기
            List<TourPlaceDto> list = objectMapper.readValue(file, new TypeReference<List<TourPlaceDto>>() {});



            List<TourPlaceDto> nearbyList = new ArrayList<>();

            for (TourPlaceDto obj : list) {

               // score(obj.getTitle(),obj.getType(),obj.getRating(),obj.getUser_ratings_total(),obj.getReviews());
                //double distance = calculateDistance(lat, lon, obj.getLat(), obj.getLng());
//
//                if (distance <= 2.0) {
//                    nearbyList.add(obj);  // 2km 이내면 담기
//                }
            }

            //return nearbyList;  // 담은 리스트 리턴
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}