package com.example.demo.trip.service;

import com.example.demo.trip.dto.PlaceDetailDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendRequest.SlotOrder;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.dto.TripRecommendResponse.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripRecommendService {

    private final ObjectMapper objectMapper;

    private static final String FOOD_PATH         = "model/tourism/food/food_scores.json";
    private static final String CAFE_PATH         = "model/tourism/cafe/cafe_scores.json";
    private static final String REVIEWS_FOOD_PATH = "model/tourism/food/reviews_food.json";
    private static final String REVIEW_CAFE_PATH  = "model/tourism/cafe/review_cafe.json";
    private static final String LOOKUP_PATH       = "model/tourism/model_lookup.json";

    // 언어별 candidates 경로
    private static final String CANDIDATE_PATH_KO = "tourdata/tourism/seoul_recommend_candidates_translated_KR.json";
    private static final String CANDIDATE_PATH_EN = "tourdata/tourism/seoul_recommend_candidates_translated_EN.json";

    // 언어별 관광지 캐시
    private List<PlaceData> tourismPlacesKo = new ArrayList<>();
    private List<PlaceData> tourismPlacesEn = new ArrayList<>();

    private List<PlaceData> foodPlaces    = new ArrayList<>();
    private List<PlaceData> cafePlaces    = new ArrayList<>();

    private Map<String, Double> foreignCoverage = new LinkedHashMap<>();
    private Map<String, Double> agePopularity   = new LinkedHashMap<>();
    private List<String> popularAreaList = new ArrayList<>();
    private List<String> allAreaList     = new ArrayList<>();

    // 리뷰 캐시: 장소명 → 리뷰 목록
    private Map<String, List<ReviewDto>> reviewCache = new HashMap<>();

    // ── 내부 장소 모델 ──
    private record PlaceData(
            String name, String category,
            double lat,  double lng,
            double score, double avgRating, int reviewCount,
            String area, String foodCategory,
            String address,   // 한국어 주소
            String imageUrl   // 대표 이미지 URL
    ) {}

    // ── AREA_COORDS ──
    private static final Map<String, double[]> AREA_COORDS;
    static {
        AREA_COORDS = new LinkedHashMap<>();
        AREA_COORDS.put("종로",   new double[]{37.5704, 126.9920});
        AREA_COORDS.put("명동",   new double[]{37.5636, 126.9827});
        AREA_COORDS.put("홍대",   new double[]{37.5563, 126.9236});
        AREA_COORDS.put("강남",   new double[]{37.4979, 127.0276});
        AREA_COORDS.put("성수",   new double[]{37.5447, 127.0556});
        AREA_COORDS.put("이태원", new double[]{37.5340, 126.9942});
        AREA_COORDS.put("잠실",   new double[]{37.5133, 127.1001});
        AREA_COORDS.put("인사동", new double[]{37.5745, 126.9855});
        AREA_COORDS.put("북촌",   new double[]{37.5826, 126.9830});
        AREA_COORDS.put("경복궁", new double[]{37.5796, 126.9770});
        AREA_COORDS.put("여의도", new double[]{37.5219, 126.9245});
        AREA_COORDS.put("동대문", new double[]{37.5666, 127.0093});
        AREA_COORDS.put("광화문", new double[]{37.5760, 126.9770});
        AREA_COORDS.put("압구정", new double[]{37.5276, 127.0285});
        AREA_COORDS.put("신촌",   new double[]{37.5556, 126.9369});
        AREA_COORDS.put("서울숲", new double[]{37.5447, 127.0374});
        AREA_COORDS.put("합정",   new double[]{37.5497, 126.9148});
        AREA_COORDS.put("마포",   new double[]{37.5538, 126.9507});
        AREA_COORDS.put("용산",   new double[]{37.5298, 126.9647});
        AREA_COORDS.put("노원",   new double[]{37.6542, 127.0568});
        AREA_COORDS.put("건대",   new double[]{37.5404, 127.0702});
        AREA_COORDS.put("왕십리", new double[]{37.5614, 127.0369});
    }

    // ────────────────────────────────────────────
    //  @PostConstruct
    // ────────────────────────────────────────────
    @PostConstruct
    public void loadAllData() {
        try {
            loadModelLookup();
            loadReviews();
            foodPlaces      = loadScoreFile(FOOD_PATH, "food");
            cafePlaces      = loadScoreFile(CAFE_PATH, "cafe");
            tourismPlacesKo = loadTourismCandidates(CANDIDATE_PATH_KO);
            tourismPlacesEn = loadTourismCandidates(CANDIDATE_PATH_EN);
            log.info("[TripService] 로드 완료 | 관광KO:{} 관광EN:{} 음식:{} 카페:{} | 리뷰캐시:{} | 지역:{}",
                    tourismPlacesKo.size(), tourismPlacesEn.size(),
                    foodPlaces.size(), cafePlaces.size(),
                    reviewCache.size(), allAreaList.size());
        } catch (Exception e) {
            log.error("[TripService] 데이터 로드 실패", e);
        }
    }

    // ────────────────────────────────────────────
    //  리뷰 캐시 로딩
    // ────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void loadReviews() throws Exception {
        // reviews_food.json : [{원본이름, 평균별점, 리뷰별점, 리뷰내용}, ...]
        loadReviewFile(REVIEWS_FOOD_PATH, "원본이름", "리뷰내용", "리뷰별점", "평균별점", null);
        // review_cafe.json  : 동일 구조
        loadReviewFile(REVIEW_CAFE_PATH,  "원본이름", "리뷰내용", "리뷰별점", "평균별점", null);
    }

    private void loadReviewFile(String path, String nameKey, String textKey,
                                String ratingKey, String avgKey, String authorKey) throws Exception {
        Object raw = readJsonRaw(path);
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List<?> arr)
            arr.forEach(o -> { if (o instanceof Map<?,?> m) items.add((Map<String,Object>) m); });

        for (Map<String, Object> m : items) {
            String name   = str(m, nameKey);
            String text   = str(m, textKey);
            double rating = dbl(m, ratingKey);
            if (name.isBlank() || text.isBlank()) continue;

            ReviewDto r = new ReviewDto();
            r.setAuthor(authorKey != null ? str(m, authorKey) : "익명");
            r.setRating(rating);
            r.setText(text);
            r.setTimeDesc("");

            reviewCache.computeIfAbsent(name, k -> new ArrayList<>()).add(r);
        }
    }

    // ────────────────────────────────────────────
    //  지역 목록 API
    // ────────────────────────────────────────────
    public Map<String, Object> getAreaData() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("popular",  popularAreaList);
        result.put("allAreas", allAreaList);
        return result;
    }

    // ────────────────────────────────────────────
    //  JSON 로더
    // ────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void loadModelLookup() throws Exception {
        Map<String, Object> root = readJson(LOOKUP_PATH, new TypeReference<>() {});
        Object fc = root.get("foreign_coverage");
        if (fc instanceof Map<?,?> m) {
            m.forEach((k, v) -> {
                String name = k.toString().trim();
                if (name.length() <= 1) return;
                if (v instanceof Number n) { foreignCoverage.put(name, n.doubleValue()); allAreaList.add(name); }
            });
        }
        Object ap = root.get("age_popularity");
        if (ap instanceof Map<?,?> m) {
            m.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number)
                    .sorted((a, b) -> Double.compare(((Number)b.getValue()).doubleValue(), ((Number)a.getValue()).doubleValue()))
                    .forEach(e -> {
                        String name = e.getKey().toString().trim();
                        if (!name.isBlank()) { agePopularity.put(name, ((Number)e.getValue()).doubleValue()); popularAreaList.add(name); }
                    });
        }
    }

    @SuppressWarnings("unchecked")
    private List<PlaceData> loadScoreFile(String path, String defaultCategory) throws Exception {
        Object raw = readJsonRaw(path);
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List<?> arr) {
            arr.forEach(o -> { if (o instanceof Map<?,?> m) items.add((Map<String,Object>) m); });
        } else if (raw instanceof Map<?,?> rootMap) {
            boolean found = false;
            for (String key : List.of("data","list","items","places","restaurants","cafes")) {
                Object inner = rootMap.get(key);
                if (inner instanceof List<?> innerArr) { innerArr.forEach(o -> { if (o instanceof Map<?,?> m) items.add((Map<String,Object>) m); }); found = true; break; }
            }
            if (!found) rootMap.forEach((k, v) -> { if (v instanceof Map<?,?> m) { Map<String,Object> item = new HashMap<>((Map<String,Object>) m); item.putIfAbsent("name", k.toString()); items.add(item); } });
        }
        List<PlaceData> list = new ArrayList<>();
        for (Map<String, Object> m : items) {
            String name     = firstStr(m, "name", "googleName");
            double lat      = firstDbl(m, "lat", "latitude");
            double lng      = firstDbl(m, "lng", "longitude");
            double score    = firstDbl(m, "finalScore", "score", "avgStar");
            double star     = firstDbl(m, "avgStar", "rating");
            int    cnt      = intVal(m, "reviewCount");
            String rawCat   = str(m, "category");
            String cat      = "cafe".equals(defaultCategory) ? "cafe" : "바".equals(rawCat) ? "bar" : "food";
            String foodCat  = "cafe".equals(defaultCategory) ? "" : rawCat;
            if (name.isBlank() || lat == 0) continue;
            // food/cafe는 주소 없음 → 빈 문자열
            list.add(new PlaceData(name, cat, lat, lng, score, star, cnt, areaFromCoord(lat, lng), foodCat, "", ""));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private List<PlaceData> loadTourismCandidates(String path) throws Exception {
        Object raw = readJsonRaw(path);
        List<Map<String, Object>> items = new ArrayList<>();
        if (raw instanceof List<?> arr) arr.forEach(o -> { if (o instanceof Map<?,?> m) items.add((Map<String,Object>) m); });
        else if (raw instanceof Map<?,?> root) {
            Object inner = ((Map<?,?>) root).get("candidates");
            if (inner instanceof List<?> arr2) arr2.forEach(o -> { if (o instanceof Map<?,?> m) items.add((Map<String,Object>) m); });
        }

        List<PlaceData> list = new ArrayList<>();
        for (Map<String, Object> m : items) {
            String name  = firstStr(m, "title", "name");
            double lat   = firstDbl(m, "lat", "latitude", "위도");
            double lng   = firstDbl(m, "lng", "longitude", "경도");
            double score = firstDbl(m, "score", "finalScore", "rating");
            double star  = firstDbl(m, "rating", "avgStar");
            int    cnt   = intVal(m, "user_ratings_total");
            String addr  = firstStr(m, "addr", "address", "google_addr");
            String img   = firstStr(m, "image", "imageUrl");

            // candidates의 reviews를 reviewCache에 추가 (아직 없으면)
            Object reviewsRaw = m.get("reviews");
            if (reviewsRaw instanceof List<?> revArr && !reviewCache.containsKey(name)) {
                List<ReviewDto> revList = new ArrayList<>();
                for (Object rv : revArr) {
                    if (rv instanceof Map<?,?> rm) {
                        ReviewDto r = new ReviewDto();
                        r.setAuthor(str((Map<String,Object>) rm, "author_name"));
                        r.setRating(dbl((Map<String,Object>) rm, "rating"));
                        r.setText(str((Map<String,Object>) rm, "text"));
                        r.setTimeDesc(str((Map<String,Object>) rm, "relative_time_description"));
                        if (!r.getText().isBlank()) revList.add(r);
                    }
                }
                if (!revList.isEmpty()) reviewCache.put(name, revList);
            }

            if (name.isBlank()) continue;
            if (lat == 0 && lng == 0) { lat = 37.5665; lng = 126.9780; }
            list.add(new PlaceData(name, "tourism", lat, lng,
                    score > 0 ? score : 1.0, star, cnt, areaFromCoord(lat, lng), "", addr, img));
        }

        if (list.isEmpty()) {
            foreignCoverage.forEach((name, cov) -> {
                double[] coord = AREA_COORDS.getOrDefault(name, new double[]{37.5665, 126.9780});
                list.add(new PlaceData(name, "tourism", coord[0], coord[1], cov, 4.0, 0, areaFromCoord(coord[0], coord[1]), "", "", ""));
            });
        }
        return list;
    }

    // ────────────────────────────────────────────
    //  추천 + 일정 생성
    // ────────────────────────────────────────────
    public TripRecommendResponse recommend(TripRecommendRequest req) {
        // 언어별 관광지 데이터 선택
        List<PlaceData> tourismPlaces = "en".equalsIgnoreCase(req.getLang())
                ? tourismPlacesEn : tourismPlacesKo;

        List<ScoredPlace> tourism = scoreAll(tourismPlaces, req);
        List<ScoredPlace> food    = scoreAll(foodPlaces.stream().filter(p -> !"bar".equals(p.category())).collect(Collectors.toList()), req);
        List<ScoredPlace> cafe    = scoreAll(cafePlaces, req);
        List<ScoredPlace> bar     = scoreAll(foodPlaces.stream().filter(p -> "bar".equals(p.category())).collect(Collectors.toList()), req);

        TripRecommendResponse resp = new TripRecommendResponse();
        resp.setTourismList(toRecList(tourism, 10));
        resp.setFoodList   (toRecList(food,    10));
        resp.setCafeList   (toRecList(cafe,    10));
        resp.setSchedule   (buildSchedule(req, tourism, food, cafe, bar, tourismPlaces));
        return resp;
    }

    private List<ScoredPlace> scoreAll(List<PlaceData> places, TripRecommendRequest req) {
        return places.stream()
                .map(p -> new ScoredPlace(p, calculateScore(p, req), buildReason(p, req)))
                .sorted(Comparator.comparingDouble(ScoredPlace::score).reversed())
                .collect(Collectors.toList());
    }

    private double calculateScore(PlaceData p, TripRecommendRequest req) {
        double s = p.score();
        if (req.getMustVisit() != null && req.getMustVisit().stream().anyMatch(mv -> p.name().contains(mv) || mv.contains(p.name()))) s += 0.35;
        if (req.getPreferredAreas() != null && req.getPreferredAreas().stream().anyMatch(a -> p.area().contains(a) || p.name().contains(a) || a.contains(p.name()))) s += 0.18;
        if (req.getCategories() != null) {
            List<String> cats = req.getCategories();
            if (cats.contains("local"))                                       s += 0.08;
            if (cats.contains("photo"))                                       s += 0.05;
            if (cats.contains("healing"))                                     s += 0.04;
            if (cats.contains("cafe")     && "cafe".equals(p.category()))    s += 0.10;
            if (cats.contains("food")     && "food".equals(p.category()))    s += 0.10;
            if (cats.contains("night")    && "bar".equals(p.category()))     s += 0.12;
            if (cats.contains("culture")  && "tourism".equals(p.category())) s += 0.06;
            if (cats.contains("nature")   && "tourism".equals(p.category())) s += 0.06;
            if (cats.contains("shopping") && "tourism".equals(p.category())) s += 0.04;
        }
        s *= foreignCoverage.getOrDefault(p.name(), 1.0);
        if ("high".equals(req.getBudget())) s += 0.03;
        if ("low".equals(req.getBudget()))  s -= 0.02;
        return Math.max(0, s);
    }

    private String buildReason(PlaceData p, TripRecommendRequest req) {
        List<String> r = new ArrayList<>();
        if (req.getMustVisit() != null && req.getMustVisit().stream().anyMatch(mv -> p.name().contains(mv) || mv.contains(p.name()))) r.add("꼭 가고 싶은 장소 반영");
        if (req.getPreferredAreas() != null && req.getPreferredAreas().stream().anyMatch(a -> p.area().contains(a) || p.name().contains(a) || a.contains(p.name()))) r.add("선호 지역 우선 반영");
        if (req.getCategories() != null) {
            List<String> cats = req.getCategories();
            if (cats.contains("local"))   r.add("로컬 체험 요소");
            if (cats.contains("photo"))   r.add("사진/감성 포인트");
            if (cats.contains("culture")) r.add("문화/역사 반영");
            if (cats.contains("nature"))  r.add("자연/공원 반영");
            if (cats.contains("healing")) r.add("힐링 코스");
        }
        if (r.isEmpty()) r.add("기본 점수 우수");
        return String.join(", ", r);
    }

    // ────────────────────────────────────────────
    //  일정 생성
    // ────────────────────────────────────────────
    private List<DayScheduleDto> buildSchedule(TripRecommendRequest req,
                                               List<ScoredPlace> tourism,
                                               List<ScoredPlace> food,
                                               List<ScoredPlace> cafe,
                                               List<ScoredPlace> bar,
                                               List<PlaceData> tourismPlaces) {
        List<SlotOrder> orders = (req.getSlotOrders() != null && !req.getSlotOrders().isEmpty())
                ? req.getSlotOrders() : buildDefaultOrders(req);

        List<DayScheduleDto> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        double anchorLat = 37.5665, anchorLng = 126.9780;
        if (req.getPreferredAreas() != null && !req.getPreferredAreas().isEmpty()) {
            for (String area : req.getPreferredAreas()) {
                // 1순위: AREA_COORDS 행정구역명 직접 매칭
                if (AREA_COORDS.containsKey(area)) {
                    double[] coord = AREA_COORDS.get(area);
                    anchorLat = coord[0]; anchorLng = coord[1];
                    break;
                }
                // 2순위: tourismPlaces에서 같은 이름 장소 좌표 사용
                Optional<PlaceData> match = tourismPlaces.stream()
                        .filter(p -> p.name().equals(area)
                                || p.name().contains(area)
                                || area.contains(p.name()))
                        .findFirst();
                if (match.isPresent() && match.get().lat() != 0) {
                    anchorLat = match.get().lat();
                    anchorLng = match.get().lng();
                    break;
                }
            }
        }

        Map<String, List<ScoredPlace>> foodByCat = new HashMap<>();
        for (ScoredPlace sp : food) foodByCat.computeIfAbsent(sp.place.foodCategory(), k -> new ArrayList<>()).add(sp);
        foodByCat.put("전체", food);

        for (int day = 1; day <= req.getTravelDays(); day++) {
            DayScheduleDto d = new DayScheduleDto();
            d.setDay(day);
            List<SlotDto> slots = new ArrayList<>();
            int hour = req.getStartHour() > 0 ? req.getStartHour() : 10;
            int end  = req.getEndHour()   > 0 ? req.getEndHour()   : 21;

            for (SlotOrder order : orders) {
                if (hour >= end) break;
                switch (order.getType()) {
                    case "tourism" -> {
                        ScoredPlace p = pickNext(tourism, used, anchorLat, anchorLng, 10.0);
                        if (p != null) { used.add(p.place.name()); slots.add(makeSlot(hour, "tourism", p, tourism)); if (p.place.lat() != 0) { anchorLat = p.place.lat(); anchorLng = p.place.lng(); } hour += 2; }
                    }
                    case "food" -> {
                        String fc = order.getFoodCategory();
                        List<ScoredPlace> pool = (fc != null && !fc.isBlank() && foodByCat.containsKey(fc)) ? foodByCat.get(fc) : food;
                        ScoredPlace p = pickNext(pool, used, anchorLat, anchorLng, 8.0);
                        if (p != null) {
                            used.add(p.place.name());
                            SlotDto slot = makeSlot(hour, "food", p, pool);
                            if (fc != null && !fc.isBlank()) slot.setReason("[" + fc + "] " + slot.getReason());
                            slots.add(slot);
                            if (p.place.lat() != 0) { anchorLat = p.place.lat(); anchorLng = p.place.lng(); }
                            hour += 1;
                        }
                    }
                    case "cafe" -> {
                        ScoredPlace p = pickNext(cafe, used, anchorLat, anchorLng, 8.0);
                        if (p != null) { used.add(p.place.name()); slots.add(makeSlot(hour, "cafe", p, cafe)); hour += 1; }
                    }
                    case "bar" -> {
                        ScoredPlace p = pickNext(bar, used, anchorLat, anchorLng, 8.0);
                        if (p != null) { used.add(p.place.name()); slots.add(makeSlot(hour, "bar", p, bar)); hour += 1; }
                    }
                }
            }
            d.setSlots(slots);
            result.add(d);
        }
        return result;
    }

    private List<SlotOrder> buildDefaultOrders(TripRecommendRequest req) {
        List<SlotOrder> orders = new ArrayList<>();
        for (int i = 0; i < req.getDailyTourism(); i++) orders.add(order("tourism", null));
        for (int i = 0; i < req.getDailyMeal();    i++) orders.add(order("food",    null));
        for (int i = 0; i < req.getDailyCafe();    i++) orders.add(order("cafe",    null));
        for (int i = 0; i < req.getDailyBar();     i++) orders.add(order("bar",     null));
        return orders;
    }

    private SlotOrder order(String type, String foodCategory) {
        SlotOrder o = new SlotOrder(); o.setType(type); o.setFoodCategory(foodCategory); return o;
    }

    private ScoredPlace pickNext(List<ScoredPlace> list, Set<String> used,
                                 double anchorLat, double anchorLng, double maxKm) {
        for (ScoredPlace sp : list) {
            if (used.contains(sp.place.name())) continue;
            if (haversine(anchorLat, anchorLng, sp.place.lat(), sp.place.lng()) <= maxKm) return sp;
        }
        return list.stream().filter(sp -> !used.contains(sp.place.name())).findFirst().orElse(null);
    }

    /** SlotDto 생성 — 주소/이미지/별점/리뷰 포함 */
    private SlotDto makeSlot(int hour, String cat, ScoredPlace pick, List<ScoredPlace> pool) {
        SlotDto s = new SlotDto();
        s.setTime(String.format("%02d:00", hour));
        s.setCategory(cat);
        s.setPlaceName(pick.place.name());
        s.setReason(pick.reason);
        s.setScore(Math.round(pick.score * 10000.0) / 10000.0);
        s.setLat(pick.place.lat());
        s.setLng(pick.place.lng());
        s.setAddress(pick.place.address());
        s.setImageUrl(pick.place.imageUrl());
        s.setAvgRating(pick.place.avgRating());
        s.setReviewCount(pick.place.reviewCount());

        // 리뷰 최대 3개
        List<ReviewDto> reviews = reviewCache.getOrDefault(pick.place.name(), new ArrayList<>());
        s.setReviews(reviews.stream().limit(3).collect(Collectors.toList()));

        // 대체 후보
        List<AlternativeDto> alts = pool.stream()
                .filter(sp -> !sp.place.name().equals(pick.place.name()))
                .limit(5)
                .map(sp -> {
                    AlternativeDto a = new AlternativeDto();
                    a.setPlaceName(sp.place.name());
                    a.setScore(Math.round(sp.score * 10000.0) / 10000.0);
                    a.setLat(sp.place.lat()); a.setLng(sp.place.lng());
                    a.setDistanceKm(Math.round(haversine(pick.place.lat(), pick.place.lng(), sp.place.lat(), sp.place.lng()) * 100.0) / 100.0);
                    return a;
                }).collect(Collectors.toList());
        s.setAlternatives(alts);
        return s;
    }

    private List<RecommendedPlace> toRecList(List<ScoredPlace> scored, int limit) {
        return scored.stream().limit(limit).map(sp -> {
            RecommendedPlace rp = new RecommendedPlace();
            rp.setName(sp.place.name()); rp.setScore(Math.round(sp.score * 10000.0) / 10000.0);
            rp.setReason(sp.reason); rp.setLat(sp.place.lat()); rp.setLng(sp.place.lng());
            rp.setAddress(sp.place.address()); rp.setImageUrl(sp.place.imageUrl());
            rp.setAvgRating(sp.place.avgRating()); rp.setReviewCount(sp.place.reviewCount());
            return rp;
        }).collect(Collectors.toList());
    }

    public PlaceDetailDto getPlaceDetail(Long placeId) { return null; }

    // ────────────────────────────────────────────
    //  유틸
    // ────────────────────────────────────────────
    private String areaFromCoord(double lat, double lng) {
        String nearest = "서울"; double minDist = Double.MAX_VALUE;
        for (Map.Entry<String, double[]> e : AREA_COORDS.entrySet()) {
            double d = haversine(lat, lng, e.getValue()[0], e.getValue()[1]);
            if (d < minDist) { minDist = d; nearest = e.getKey(); }
        }
        return nearest;
    }
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2-lat1), dLng = Math.toRadians(lng2-lng1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLng/2)*Math.sin(dLng/2);
        return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
    }
    private <T> T readJson(String path, TypeReference<T> type) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream is = res.getInputStream()) { return objectMapper.readValue(is, type); }
    }
    private Object readJsonRaw(String path) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream is = res.getInputStream()) {
            ObjectMapper nm = objectMapper.copy().enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
            return nm.readValue(is, Object.class);
        }
    }
    private String firstStr(Map<String, Object> m, String... keys) {
        for (String k : keys) { Object v = m.get(k); if (v instanceof String s && !s.isBlank()) return s; } return "";
    }
    private double firstDbl(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof String s && !s.isBlank()) {
                try { return Double.parseDouble(s); } catch (Exception ignored) {}
            }
        }
        return 0.0;
    }
    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key); return v instanceof String s ? s : (v != null ? v.toString() : "");
    }
    private double dbl(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) { try { return Double.parseDouble(s); } catch (Exception ignored) {} }
        return 0.0;
    }
    private int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key); return v instanceof Number n ? n.intValue() : 0;
    }
    private record ScoredPlace(PlaceData place, double score, String reason) {}
}