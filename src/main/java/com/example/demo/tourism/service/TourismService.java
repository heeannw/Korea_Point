package com.example.demo.tourism.service;

import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.config.DeeplTranslator;
import com.example.demo.tourism.dto.FoodPopularityDto;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.dto.TourPopularityDto;
import com.example.demo.tourism.dto.TourReviewDto;
import com.example.demo.tourism.repository.PlaceReviewRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TourismService {

    private static final double DEFAULT_NEARBY_RADIUS_KM = 2.0;
    private static final int DEFAULT_POPULAR_LIMIT = 20;
    private static final Set<String> TOUR_CONTENT_TYPES = Set.of("12", "14", "15", "25", "28", "38");

    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
            "amazing", "beautiful", "great", "wonderful", "nice", "love", "best",
            "awesome", "fantastic", "perfect", "excellent", "good", "peaceful",
            "clean", "fun", "interesting", "impressive", "recommend", "must", "enjoy",
            "enjoyed", "worth", "highly", "definitely", "friendly", "pretty",
            "stunning", "lovely", "delightful", "remarkable", "breathtaking",
            "charming", "magnificent", "superb", "splendid", "well", "easy",
            "free", "traditional", "unique", "special", "view",
            "좋아요", "좋다", "좋았", "추천", "친절", "깨끗", "예쁘", "아름답", "재미", "최고"
    ));

    private final TourApiClient tourApiClient;
    private final TourPopularityProvider popularityProvider;
    private final FoodPopularityProvider foodPopularityProvider;
    private final DeeplTranslator deepl;
    private final PlaceReviewRepository placeReviewRepository;
    /** 상세 개요는 KorService2에서 국문만 온다. 요청 언어로 한 번 번역하면 재사용한다. key = contentId|lang */
    private final Map<String, String> overviewCache = new java.util.concurrent.ConcurrentHashMap<>();

    public TourismService(TourApiClient tourApiClient, TourPopularityProvider popularityProvider,
                          FoodPopularityProvider foodPopularityProvider, DeeplTranslator deepl,
                          PlaceReviewRepository placeReviewRepository) {
        this.tourApiClient = tourApiClient;
        this.popularityProvider = popularityProvider;
        this.foodPopularityProvider = foodPopularityProvider;
        this.deepl = deepl;
        this.placeReviewRepository = placeReviewRepository;
    }

    /**
     * 우리 사용자 리뷰의 평균 별점·개수를 목록에 얹는다.
     * place_review 전체 GROUP BY 한 방. 표가 작아 캐시 없이 매 요청 조회한다.
     * ponytail: place_review가 수만 건 규모로 커지면 짧은 TTL 캐시를 붙일 것.
     */
    private void applyUserRatings(List<TourPlaceDto> places) {
        Map<String, Object[]> byId = new HashMap<>();
        for (Object[] row : placeReviewRepository.aggregateRatingByContent()) {
            byId.put((String) row[0], row);
        }
        if (byId.isEmpty()) return;
        for (TourPlaceDto place : places) {
            Object[] row = byId.get(place.getId());
            if (row == null) continue;
            place.setUserRating(Math.round(((Number) row[1]).doubleValue() * 10) / 10.0);
            place.setUserReviewCount(((Number) row[2]).intValue());
        }
    }

    /**
     * 인기 순위를 목록에 얹는다. 공급자가 아직 준비되지 않았으면 아무것도 하지 않아
     * 화면에서 인기 표시가 통째로 감춰진다.
     */
    private void applyPopularity(List<TourPlaceDto> places, String lang) {
        if (!popularityProvider.isAvailable()) return;

        Map<String, TourPopularityDto> byContentId = popularityProvider.findPopularity(lang);
        if (byContentId.isEmpty()) return;

        for (TourPlaceDto place : places) {
            TourPopularityDto popularity = byContentId.get(place.getId());
            if (popularity == null) continue;
            place.setPopularRank(popularity.getPopularRank());
            place.setPopularCategory(popularity.getPopularCategory());
            place.setPopularityScore(popularity.getPopularityScore());
        }
    }

    /** 부산 관광지 원본은 저장 파일이 아니라 TourAPI에서 매 요청마다 가져온다. */
    public List<TourPlaceDto> allList(String lang) {
        List<TourPlaceDto> places = tourApiClient.findBusanPlaces(lang).stream()
                .filter(this::isTourContent)
                .collect(Collectors.toCollection(ArrayList::new));
        applyPopularity(places, lang);
        applyUserRatings(places);
        scoreAndSort(places);
        return places;
    }

    /** 음식점 목록이다. 카페는 별도 화면으로 빠져서 제외한다. */
    public List<TourPlaceDto> foodList(String lang) {
        return busanFood(lang, false);
    }

    /** 카페·찻집 목록이다. TourAPI는 카페를 음식점 유형 안에 담고 있어 같은 응답에서 갈라낸다. */
    public List<TourPlaceDto> cafeList(String lang) {
        return busanFood(lang, true);
    }

    private List<TourPlaceDto> busanFood(String lang, boolean cafeOnly) {
        List<TourPlaceDto> places = new ArrayList<>(tourApiClient.findBusanFoodPlaces(lang));
        places.removeIf(place ->
                TourApiClient.CAFE_CATEGORIES.contains(place.getFoodCategory()) != cafeOnly);
        // TourAPI 부산 응답에 실제 항목이 없는 주점 분류는 음식점 화면에서 제외한다.
        if (!cafeOnly) places.removeIf(place -> "bar".equals(place.getFoodCategory()));
        applyFoodPopularity(places, lang, cafeOnly, DEFAULT_POPULAR_LIMIT);
        applyUserRatings(places);
        scoreAndSort(places);
        return places;
    }

    /**
     * 실제 인기 공급자가 연결된 경우에만 음식점 또는 카페 TOP N을 반환한다.
     * 현재 빈 공급자 상태에서는 TourAPI까지 호출하지 않고 빈 목록을 즉시 반환한다.
     */
    public List<TourPlaceDto> popularFoodList(String lang, boolean cafeOnly, int limit) {
        if (!foodPopularityProvider.isAvailable()) return List.of();

        int safeLimit = Math.max(1, Math.min(limit, DEFAULT_POPULAR_LIMIT));
        return busanFood(lang, cafeOnly).stream()
                .filter(place -> place.getPopularRank() != null)
                .sorted(Comparator.comparing(TourPlaceDto::getPopularRank))
                .limit(safeLimit)
                .toList();
    }

    private void applyFoodPopularity(List<TourPlaceDto> places, String lang,
                                     boolean cafeOnly, int limit) {
        if (!foodPopularityProvider.isAvailable()) return;

        String section = cafeOnly
                ? FoodPopularityProvider.CAFE_SECTION
                : FoodPopularityProvider.FOOD_SECTION;
        Map<String, FoodPopularityDto> byContentId =
                foodPopularityProvider.findPopularity(section, lang, limit);
        if (byContentId.isEmpty()) return;

        for (TourPlaceDto place : places) {
            FoodPopularityDto popularity = byContentId.get(place.getId());
            if (popularity == null) continue;
            place.setPopularRank(popularity.getPopularRank());
            place.setPopularCategory(popularity.getSection());
            place.setPopularityScore(popularity.getPopularityScore());
            place.setPopularityBasis(popularity.getPopularityBasis());
            place.setPopularityCalculatedAt(popularity.getCalculatedAt());
        }
    }

    /** 음식점 목록에서 키워드로 거른다. TourAPI 키워드 검색은 유형을 섞어 주기 때문이다. */
    public List<TourPlaceDto> foodSearch(String keyword, Double userLat, Double userLon,
                                         String lang, boolean cafeOnly) {
        List<TourPlaceDto> places = cafeOnly ? cafeList(lang) : foodList(lang);
        if (keyword != null && !keyword.isBlank()) {
            String needle = keyword.trim().toLowerCase();
            places.removeIf(place -> place.getTitle() == null
                    || !place.getTitle().toLowerCase().contains(needle));
        }
        if (userLat != null && userLon != null) applyDistances(places, userLat, userLon);
        return places;
    }

    public List<TourPlaceDto> analyzeLocation(Double lat, Double lon, String lang) {
        if (lat == null || lon == null) return List.of();
        List<TourPlaceDto> places = tourApiClient
                .findNearby(lat, lon, DEFAULT_NEARBY_RADIUS_KM, null, lang).stream()
                .filter(this::isTourContent)
                .collect(Collectors.toCollection(ArrayList::new));
        applyDistances(places, lat, lon);
        scoreAndSort(places);
        return places;
    }

    public List<TourPlaceDto> searchKeyword(String keyword, String lang) {
        return searchKeyword(keyword, null, null, lang);
    }

    public List<TourPlaceDto> searchKeyword(String keyword, Double userLat, Double userLon, String lang) {
        if (keyword == null || keyword.isBlank()) return List.of();
        List<TourPlaceDto> places = tourApiClient.searchBusan(keyword.trim(), lang).stream()
                .filter(this::isTourContent)
                .collect(Collectors.toCollection(ArrayList::new));
        if (userLat != null && userLon != null) applyDistances(places, userLat, userLon);
        scoreAndSort(places);
        return places;
    }

    public List<TourPlaceDto> getNearbyFoodPlaces(Double lat, Double lon, double radiusKm, String lang) {
        if (lat == null || lon == null) return List.of();
        List<TourPlaceDto> places = new ArrayList<>(tourApiClient.findNearby(
                lat, lon, radiusKm, TourApiClient.FOOD_CONTENT_TYPE, lang));
        applyDistances(places, lat, lon);
        scoreAndSort(places);
        return places;
    }

    public Map<String, Object> getDetail(String contentId, String contentTypeId, String lang) {
        Map<String, Object> detail = tourApiClient.findDetail(contentId, contentTypeId, lang);
        translateOverview(detail, contentId, lang);
        return detail;
    }

    /** 비-국문 요청이면 개요를 DeepL로 번역해 끼운다. 실패·한도 초과 시 국문 원문 유지. */
    private void translateOverview(Map<String, Object> detail, String contentId, String lang) {
        if (lang == null || "ko".equalsIgnoreCase(lang)) return;
        Object raw = detail.get("overview");
        if (!(raw instanceof String ko) || ko.isBlank()) return;
        String plain = ko.replaceAll("<[^>]+>", " ").trim();   // 화면에서 어차피 태그를 벗기므로 번역 전에 정리
        String cached = overviewCache.computeIfAbsent(contentId + "|" + lang, k -> {
            String t = deepl.translate(plain, lang);
            return t != null ? t : ko;   // 실패해도 캐시(국문) — 같은 장소 재요청 시 DeepL 재호출 방지
        });
        detail.put("overview", cached);
    }

    /**
     * 리뷰가 있으면 평점/감성분석을 반영하고, 없으면 0점 처리하지 않고
     * TourAPI 정보 완성도와 콘텐츠 유형을 이용한 기본 점수를 사용한다.
     */
    public double score(String title, String googleName, String typeCode, double rating,
                        int userRatingsTotal, List<TourReviewDto> reviews) {
        List<TourReviewDto> safeReviews = reviews == null ? List.of() : reviews;
        double base = 50.0;

        if (title != null && !title.isBlank()) base += 8.0;
        base += categoryScore(typeCode);

        if (!safeReviews.isEmpty()) {
            double reviewRating = safeReviews.stream()
                    .filter(r -> r.getRating() != null)
                    .mapToDouble(TourReviewDto::getRating)
                    .average()
                    .orElse(rating > 0 ? rating : 3.0);
            double sentiment = sentimentRatio(safeReviews);
            double popularity = Math.min(Math.log1p(Math.max(userRatingsTotal, safeReviews.size()))
                    / Math.log1p(1000), 1.0);
            base = 20.0 + (reviewRating / 5.0 * 40.0) + (sentiment * 30.0) + (popularity * 10.0);
        }

        return Math.max(0.0, Math.min(base, 100.0));
    }

    public List<TourPlaceDto> allDistence(double lat, double lon, List<TourPlaceDto> displayList) {
        applyDistances(displayList, lat, lon);
        return displayList;
    }

    private void scoreAndSort(List<TourPlaceDto> places) {
        for (TourPlaceDto place : places) {
            double result = score(place.getTitle(), place.getGoogle_name(), place.getType(),
                    place.getRating() != null ? place.getRating() : 0.0,
                    place.getUser_ratings_total(), place.getReviews());
            if (place.getImage() != null && !place.getImage().isBlank()) result += 7.0;
            if (place.getAddr() != null && !place.getAddr().isBlank()) result += 5.0;
            place.setScore((float) Math.min(result, 100.0));
        }
        places.sort((a, b) -> Float.compare(nullSafeScore(b), nullSafeScore(a)));
    }

    private void applyDistances(List<TourPlaceDto> places, double lat, double lon) {
        for (TourPlaceDto place : places) {
            if (place.getLat() == null || place.getLng() == null) continue;
            double distance = haversine(lat, lon, place.getLat(), place.getLng());
            place.setDistance(Math.round(distance * 1000.0) / 1000.0);
        }
    }

    private boolean isTourContent(TourPlaceDto place) {
        return place.getType() != null && TOUR_CONTENT_TYPES.contains(place.getType());
    }

    private double categoryScore(String typeCode) {
        if (typeCode == null) return 5.0;
        return switch (typeCode) {
            case "12" -> 15.0; // 관광지
            case "25" -> 14.0; // 여행코스
            case "15" -> 13.0; // 행사/공연
            case "14" -> 12.0; // 문화시설
            case "28" -> 11.0; // 레포츠
            case "38" -> 10.0; // 쇼핑
            case "39" -> 10.0; // 음식점
            default -> 5.0;
        };
    }

    private double sentimentRatio(List<TourReviewDto> reviews) {
        long evaluated = 0;
        long positive = 0;
        for (TourReviewDto review : reviews) {
            String text = review.getText();
            if (text == null || text.isBlank()) continue;
            evaluated++;
            String normalized = text.toLowerCase();
            if (POSITIVE_WORDS.stream().anyMatch(normalized::contains)) positive++;
        }
        return evaluated == 0 ? 0.5 : (double) positive / evaluated;
    }

    private float nullSafeScore(TourPlaceDto place) {
        return place.getScore() == null ? 0.0f : place.getScore();
    }

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double earthRadiusKm = 6371.0;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaPhi = Math.toRadians(lat2 - lat1);
        double deltaLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2)
                * Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
