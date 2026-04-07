package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.dto.TourReviewDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

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
        put("25", 0.10f); put("38", 0.08f); put("15", 0.07f);
        put("12", 0.04f); put("14", 0.02f); put("28", 0.00f);
    }};

    private static final float RATING_MIN    = 1.0f;
    private static final float RATING_MAX    = 5.0f;
    private static final float LOG_TOTAL_MAX = (float) Math.log1p(70_000);
    private static final float POS_RATIO_MAX = 0.25f;
    private static final int   BAYESIAN_C    = 100;

    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "amazing","beautiful","great","wonderful","nice","love","best",
            "awesome","fantastic","perfect","excellent","good","peaceful",
            "clean","fun","interesting","impressive","recommend","must","enjoy",
            "enjoyed","worth","highly","definitely","friendly","pretty",
            "stunning","lovely","delightful","remarkable","breathtaking",
            "charming","magnificent","superb","splendid","well","easy",
            "free","traditional","unique","special","view"
    ));

    private static final Set<String> FOREIGN_LANGS = new HashSet<>(Arrays.asList(
            "en","zh","zh-Hant","zh-Hans","ja","fr","de","es","th","ru","id"
    ));

    // ── 룩업테이블 ───────────────────────────────────────────
    private double globalAvgRating = 4.47;
    private double agePopMax       = 74.5;
    private final Map<String, Double> foreignCoverage = new HashMap<>();
    private final Map<String, Double> agePopularity   = new HashMap<>();

    // 캐시 - 서버 시작 시 한 번만 로드
    private List<TourPlaceDto> cachedCandidates = null;

    private final ObjectMapper mapper = new ObjectMapper();

    // ─────────────────────────────────────────────────────────
    // 초기화 - 서버 시작 시 모든 데이터 로드
    // ─────────────────────────────────────────────────────────
    @PostConstruct
    public void init() {
        try {
            loadLookup();
            loadCandidates();   // 캐싱
            System.out.println("✅ TourismService 초기화 완료");
            System.out.println("   candidates=" + cachedCandidates.size() + "개"
                    + " coverage=" + foreignCoverage.size() + "개"
                    + " agePop=" + agePopularity.size() + "개");
        } catch (Exception e) {
            System.out.println("⚠️ TourismService 로드 실패");
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

    // JSON 한 번만 파싱해서 캐싱
    private void loadCandidates() throws Exception {
        InputStream is = getClass().getClassLoader()
                .getResourceAsStream("data/tourism/seoul_recommend_candidates.json");
        if (is == null) throw new RuntimeException("seoul_recommend_candidates.json 없음");
        cachedCandidates = mapper.readValue(is, new TypeReference<>() {});
    }

    // ─────────────────────────────────────────────────────────
    // 현위치 기반 2km 이내 추천 (캐시 사용으로 빠름)
    // ─────────────────────────────────────────────────────────
    public List<TourPlaceDto> analyzeLocation(Double lat, Double lon) {
        List<TourPlaceDto> result = new ArrayList<>();

        for (TourPlaceDto p : cachedCandidates) {
            if (p.getLat() == null || p.getLng() == null) continue;

            double dist = haversine(lat, lon, p.getLat(), p.getLng());
            if (dist > 2.0) continue;

            // ✅ 원본 수정 방지 - 복사본 사용
            TourPlaceDto copy = copy(p);
            double s = score(copy.getTitle(), copy.getType(),
                    copy.getRating() != null ? copy.getRating() : 0.0,
                    copy.getUser_ratings_total(), copy.getReviews());
            copy.setScore((float) s);
            copy.setDistance(Math.round(dist * 1000.0) / 1000.0);
            result.add(copy);
        }

        result.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 키워드 검색 + 내 위치까지 거리 표시
    // ─────────────────────────────────────────────────────────
    public List<TourPlaceDto> searchKeyword(String keyword) {
        return searchKeyword(keyword, null, null);
    }

    // 내 위치 좌표를 함께 받으면 distance 계산
    public List<TourPlaceDto> searchKeyword(String keyword, Double userLat, Double userLon) {
        if (keyword == null || keyword.isBlank()) return List.of();

        String kw = keyword.trim().toLowerCase();
        List<TourPlaceDto> result = new ArrayList<>();

        for (TourPlaceDto p : cachedCandidates) {
            String title  = p.getTitle()       != null ? p.getTitle().toLowerCase()       : "";
            String gName  = p.getGoogle_name() != null ? p.getGoogle_name().toLowerCase() : "";
            String addr   = p.getAddr()        != null ? p.getAddr().toLowerCase()        : "";

            if (!title.contains(kw) && !gName.contains(kw) && !addr.contains(kw)) continue;

            TourPlaceDto copy = copy(p);

            // 내 위치가 있으면 거리 계산
            if (userLat != null && userLon != null && copy.getLat() != null && copy.getLng() != null) {
                double dist = haversine(userLat, userLon, copy.getLat(), copy.getLng());
                copy.setDistance(Math.round(dist * 1000.0) / 1000.0);
            }

            result.add(copy);
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────
    // Save to Itinerary - 특정 장소 근처 카페/음식점 조회
    // ─────────────────────────────────────────────────────────

    // 음식점/카페에 해당하는 type 코드 (38=시장/쇼핑 포함, 추후 확장 가능)
    // 현재 candidates에 카페/음식점 별도 타입 없으므로 키워드 기반으로 필터
    private static final Set<String> FOOD_KEYWORDS = new HashSet<>(Arrays.asList(
            "식당","음식","카페","커피","맛집","레스토랑","분식","치킨","피자",
            "restaurant","cafe","coffee","food","dining","bistro","bar"
    ));

    public List<TourPlaceDto> getNearbyFoodPlaces(Double lat, Double lon, double radiusKm) {
        List<TourPlaceDto> result = new ArrayList<>();

        for (TourPlaceDto p : cachedCandidates) {
            if (p.getLat() == null || p.getLng() == null) continue;

            double dist = haversine(lat, lon, p.getLat(), p.getLng());
            if (dist > radiusKm) continue;

            // 음식 관련 키워드 포함 여부 확인
            String title  = p.getTitle()       != null ? p.getTitle().toLowerCase()       : "";
            String gName  = p.getGoogle_name() != null ? p.getGoogle_name().toLowerCase() : "";
            String addr   = p.getAddr()        != null ? p.getAddr().toLowerCase()        : "";
            String combined = title + " " + gName + " " + addr;

            boolean isFoodPlace = FOOD_KEYWORDS.stream().anyMatch(combined::contains);
            // 시장/쇼핑(38) 타입도 포함 (광장시장, 남대문시장 등)
            boolean isMarket = "38".equals(p.getType());

            if (!isFoodPlace && !isMarket) continue;

            TourPlaceDto copy = copy(p);
            double s = score(copy.getTitle(), copy.getType(),
                    copy.getRating() != null ? copy.getRating() : 0.0,
                    copy.getUser_ratings_total(), copy.getReviews());
            copy.setScore((float) s);
            copy.setDistance(Math.round(dist * 1000.0) / 1000.0);
            result.add(copy);
        }

        result.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        return result;
    }

    // ─────────────────────────────────────────────────────────
    // 점수 계산 (0~100)
    // ─────────────────────────────────────────────────────────
    public double score(String title, String typeCode, double rating,
                        int userRatingsTotal, List<TourReviewDto> reviews) {
        if (reviews == null) reviews = List.of();

        double[] feat = extractFeatures(title, rating, userRatingsTotal, reviews);
        double bay    = bayesianRating(feat[0], userRatingsTotal);
        float bonus   = CATEGORY_BONUS.getOrDefault(typeCode, 0.0f);

        double base = W_RATING     * normRating(bay)
                + W_SENTIMENT  * normSentiment(feat[2])
                + W_POPULARITY * normLogTotal(userRatingsTotal)
                + W_FOREIGN    * Math.min(feat[1], 1.0)
                + W_COVERAGE   * Math.min(feat[3], 1.0)
                + W_AGE_POP    * normAgePop(feat[4]);

        return Math.min(base * (1 + bonus) * 100.0, 100.0);
    }

    // ─────────────────────────────────────────────────────────
    // 피처 추출
    // ─────────────────────────────────────────────────────────
    private double[] extractFeatures(String title, double rating,
                                     int total, List<TourReviewDto> reviews) {
        List<TourReviewDto> foreignReviews = reviews.stream()
                .filter(r -> FOREIGN_LANGS.contains(r.getLanguage()))
                .collect(Collectors.toList());

        double foreignRatio = reviews.isEmpty() ? 0.0
                : (double) foreignReviews.size() / reviews.size();

        String text = foreignReviews.stream()
                .map(r -> r.getText() != null ? r.getText().toLowerCase() : "")
                .collect(Collectors.joining(" "));
        int wordCount = Math.max(text.trim().split("\\s+").length, 1);
        long posHits  = POSITIVE_WORDS.stream().filter(text::contains).count();
        double foreignSentiment = foreignReviews.isEmpty() ? 0.0 : (double) posHits / wordCount;

        double effectiveRating = rating;
        OptionalDouble avg = foreignReviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToDouble(TourReviewDto::getRating)
                .average();
        if (avg.isPresent()) effectiveRating = avg.getAsDouble();

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
    private double normRating(double v) { return (v - RATING_MIN) / (RATING_MAX - RATING_MIN); }
    private double normLogTotal(int total) { return Math.min(Math.log1p(total) / LOG_TOTAL_MAX, 1.0); }
    private double normSentiment(double v) { return Math.min(v / POS_RATIO_MAX, 1.0); }
    private double normAgePop(double v) { return Math.min(v / agePopMax, 1.0); }

    private double lookupWithPartial(Map<String, Double> map, String title) {
        if (title == null) return 0.0;
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

    // 원본 보호용 얕은 복사
    private TourPlaceDto copy(TourPlaceDto p) {
        TourPlaceDto c = new TourPlaceDto();
        c.setId(p.getId());
        c.setTitle(p.getTitle());
        c.setType(p.getType());
        c.setAddr(p.getAddr());
        c.setLat(p.getLat());
        c.setLng(p.getLng());
        c.setImage(p.getImage());
        c.setGoogle_place_id(p.getGoogle_place_id());
        c.setGoogle_name(p.getGoogle_name());
        c.setGoogle_addr(p.getGoogle_addr());
        c.setRating(p.getRating());
        c.setUser_ratings_total(p.getUser_ratings_total());
        c.setReviews(p.getReviews());
        return c;
    }
}