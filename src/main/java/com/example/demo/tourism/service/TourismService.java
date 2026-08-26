package com.example.demo.tourism.service;

import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.dto.TourReviewDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TourismService {

    private static final double DEFAULT_NEARBY_RADIUS_KM = 2.0;
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

    public TourismService(TourApiClient tourApiClient) {
        this.tourApiClient = tourApiClient;
    }

    /** 부산 관광지 원본은 저장 파일이 아니라 TourAPI에서 매 요청마다 가져온다. */
    public List<TourPlaceDto> allList(String lang) {
        List<TourPlaceDto> places = tourApiClient.findBusanPlaces(lang).stream()
                .filter(this::isTourContent)
                .collect(Collectors.toCollection(ArrayList::new));
        scoreAndSort(places);
        return places;
    }

    /** 음식점과 카페를 한 목록으로 돌려준다. TourAPI가 둘을 같은 유형으로 관리한다. */
    public List<TourPlaceDto> foodList(String lang) {
        List<TourPlaceDto> places = new ArrayList<>(tourApiClient.findBusanFoodPlaces(lang));
        scoreAndSort(places);
        return places;
    }

    /** 음식점 목록에서 키워드로 거른다. TourAPI 키워드 검색은 유형을 섞어 주기 때문이다. */
    public List<TourPlaceDto> foodSearch(String keyword, Double userLat, Double userLon, String lang) {
        List<TourPlaceDto> places = foodList(lang);
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
        return tourApiClient.findDetail(contentId, contentTypeId, lang);
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
