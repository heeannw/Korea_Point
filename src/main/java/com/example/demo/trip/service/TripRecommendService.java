package com.example.demo.trip.service;

import com.example.demo.trip.dto.PlaceDetailDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendRequest.SlotOrder;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.dto.TripRecommendResponse.*;
import com.example.demo.tourism.service.UnsplashImageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripRecommendService {

    private final ObjectMapper objectMapper;
    private final UnsplashImageService unsplashImageService;

    private static final String FOOD_PATH         = "model/tourism/food/food_scores.json";
    private static final String FOOD_PATH_EN      = "model/tourism/food/food_scores_EN.json";
    private static final String CAFE_PATH         = "model/tourism/cafe/cafe_scores.json";
    private static final String CAFE_PATH_EN      = "model/tourism/cafe/cafe_scores_EN.json";
    private static final String REVIEWS_FOOD_PATH = "model/tourism/food/reviews_food.json";
    private static final String REVIEW_CAFE_PATH  = "model/tourism/cafe/review_cafe.json";
    private static final String LOOKUP_PATH       = "model/tourism/model_lookup.json";

    // 언어별 candidates 경로
    private static final String CANDIDATE_PATH_KO = "tourdata/tourism/seoul_recommend_candidates_translated_KR.json";
    private static final String CANDIDATE_PATH_EN = "tourdata/tourism/seoul_recommend_candidates_translated_EN.json";

    // 언어별 관광지 캐시
    private List<PlaceData> tourismPlacesKo = new ArrayList<>();
    private List<PlaceData> tourismPlacesEn = new ArrayList<>();

    private List<PlaceData> foodPlacesKo = new ArrayList<>();
    private List<PlaceData> foodPlacesEn = new ArrayList<>();
    private List<PlaceData> cafePlacesKo = new ArrayList<>();
    private List<PlaceData> cafePlacesEn = new ArrayList<>();

    private Map<String, Double> foreignCoverage = new LinkedHashMap<>();
    private Map<String, Double> agePopularity   = new LinkedHashMap<>();
    private List<String> popularAreaList = new ArrayList<>();
    private List<String> allAreaList     = new ArrayList<>();

    // 리뷰 캐시: 장소명 → 리뷰 목록
    // 카테고리별 리뷰 캐시 (이름 충돌 방지)
    private Map<String, List<ReviewDto>> reviewCacheFood = new HashMap<>();
    private Map<String, List<ReviewDto>> reviewCacheCafe = new HashMap<>();
    private Map<String, List<ReviewDto>> reviewCacheTourism = new HashMap<>();

    // ── 내부 장소 모델 ──
    private record PlaceData(
            String name, String category,
            double lat,  double lng,
            double score, double avgRating, int reviewCount,
            String area, String foodCategory,
            String address,   // 한국어 주소
            String imageUrl,  // 대표 이미지 URL
            String nameEn,    // 로마자 변환 이름 (EN용)
            String categoryEn // 영어 카테고리 (EN용)
    ) {}

    // ── AREA_COORDS ──
    private static final Map<String, double[]> AREA_COORDS;
    static {
        AREA_COORDS = new LinkedHashMap<>();
        AREA_COORDS.put("해운대", new double[]{35.1631, 129.1635});
        AREA_COORDS.put("광안리", new double[]{35.1532, 129.1187});
        AREA_COORDS.put("감천문화마을", new double[]{35.0975, 129.0106});
        AREA_COORDS.put("남포동", new double[]{35.1007, 129.0305});
        AREA_COORDS.put("영도", new double[]{35.0787, 129.0444});
        AREA_COORDS.put("송도", new double[]{35.0764, 129.0238});
        AREA_COORDS.put("기장", new double[]{35.2446, 129.2157});
        AREA_COORDS.put("서면", new double[]{35.1578, 129.0590});
        AREA_COORDS.put("동래", new double[]{35.2050, 129.0838});
        AREA_COORDS.put("송정", new double[]{35.1786, 129.1997});
        AREA_COORDS.put("다대포", new double[]{35.0465, 128.9667});
        AREA_COORDS.put("부산역", new double[]{35.1151, 129.0414});
        // ── 한국어 키 ──
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
        // ── 영어 키 (EN 버전 preferredAreas 매칭용) ──
        AREA_COORDS.put("Jongno",                        new double[]{37.5704, 126.9920});
        AREA_COORDS.put("Myeongdong",                    new double[]{37.5636, 126.9827});
        AREA_COORDS.put("Hongdae",                       new double[]{37.5563, 126.9236});
        AREA_COORDS.put("Gangnam",                       new double[]{37.4979, 127.0276});
        AREA_COORDS.put("Seongsu",                       new double[]{37.5447, 127.0556});
        AREA_COORDS.put("Itaewon",                       new double[]{37.5340, 126.9942});
        AREA_COORDS.put("Jamsil",                        new double[]{37.5133, 127.1001});
        AREA_COORDS.put("Insadong",                      new double[]{37.5745, 126.9855});
        AREA_COORDS.put("Insa-dong Cultural Street",     new double[]{37.5745, 126.9855});
        AREA_COORDS.put("Bukchon",                       new double[]{37.5826, 126.9830});
        AREA_COORDS.put("Bukchon Hanok Village",         new double[]{37.5826, 126.9830});
        AREA_COORDS.put("Gyeongbokgung",                 new double[]{37.5796, 126.9770});
        AREA_COORDS.put("Gyeongbokgung Palace",          new double[]{37.5796, 126.9770});
        AREA_COORDS.put("Yeouido",                       new double[]{37.5219, 126.9245});
        AREA_COORDS.put("Yeouido Hangang Park",          new double[]{37.5219, 126.9245});
        AREA_COORDS.put("Dongdaemun",                    new double[]{37.5666, 127.0093});
        AREA_COORDS.put("Dongdaemun Design Plaza (DDP)", new double[]{37.5666, 127.0093});
        AREA_COORDS.put("Gwanghwamun",                   new double[]{37.5760, 126.9770});
        AREA_COORDS.put("Gwanghwamun Square",            new double[]{37.5760, 126.9770});
        AREA_COORDS.put("Apgujeong",                     new double[]{37.5276, 127.0285});
        AREA_COORDS.put("Apgujeong Rodeo Street",        new double[]{37.5276, 127.0285});
        AREA_COORDS.put("Sinchon",                       new double[]{37.5556, 126.9369});
        AREA_COORDS.put("Seoul Forest",                  new double[]{37.5447, 127.0374});
        AREA_COORDS.put("Hapjeong",                      new double[]{37.5497, 126.9148});
        AREA_COORDS.put("Mapo",                          new double[]{37.5538, 126.9507});
        AREA_COORDS.put("Yongsan",                       new double[]{37.5298, 126.9647});
        AREA_COORDS.put("Nowon",                         new double[]{37.6542, 127.0568});
        AREA_COORDS.put("Konkuk",                        new double[]{37.5404, 127.0702});
        AREA_COORDS.put("Wangsimni",                     new double[]{37.5614, 127.0369});
        AREA_COORDS.put("Namsan Seoul Tower",            new double[]{37.5512, 126.9882});
        AREA_COORDS.put("N Seoul Tower",                 new double[]{37.5512, 126.9882});
        AREA_COORDS.put("Starfield COEX Mall",           new double[]{37.5115, 127.0595});
        AREA_COORDS.put("COEX",                          new double[]{37.5115, 127.0595});
        AREA_COORDS.put("Gwangjang Market",              new double[]{37.5700, 127.0089});
        AREA_COORDS.put("Namdaemun Market",              new double[]{37.5600, 126.9757});
        AREA_COORDS.put("Lotte World Adventure",         new double[]{37.5111, 127.0980});
    }

    // ────────────────────────────────────────────
    //  @PostConstruct
    // ────────────────────────────────────────────
    @PostConstruct
    public void loadAllData() {
        try {
            tourismPlacesKo = buildBusanTourismPlaces();
            foodPlacesKo = buildBusanFoodPlaces();
            cafePlacesKo = buildBusanCafePlaces();
            tourismPlacesEn = tourismPlacesKo;
            foodPlacesEn = foodPlacesKo;
            cafePlacesEn = cafePlacesKo;
            popularAreaList = new ArrayList<>(List.of("해운대", "광안리", "감천문화마을", "남포동", "영도", "송도", "기장", "서면"));
            allAreaList = new ArrayList<>(List.of("해운대", "광안리", "감천문화마을", "남포동", "영도", "송도", "기장", "서면", "동래", "송정", "다대포", "부산역"));
            log.info("[TripService] 로드 완료 | 관광KO:{} 관광EN:{} 음식KO:{} 음식EN:{} 카페KO:{} 카페EN:{} | 리뷰캐시:{} | 지역:{}",
                    tourismPlacesKo.size(), tourismPlacesEn.size(),
                    foodPlacesKo.size(), foodPlacesEn.size(),
                    cafePlacesKo.size(), cafePlacesEn.size(),
                    reviewCacheFood.size() + reviewCacheCafe.size() + reviewCacheTourism.size(), allAreaList.size());
        } catch (Exception e) {
            log.error("[TripService] 데이터 로드 실패", e);
        }
    }

    private List<PlaceData> buildBusanTourismPlaces() {
        return List.of(
                busanPlace("해운대해수욕장", "tourism", 35.1587, 129.1604, "해운대", "부산 해운대구 우동", 4.7),
                busanPlace("광안리해수욕장", "tourism", 35.1532, 129.1187, "광안리", "부산 수영구 광안해변로", 4.7),
                busanPlace("감천문화마을", "tourism", 35.0975, 129.0106, "감천문화마을", "부산 사하구 감내2로", 4.6),
                busanPlace("태종대유원지", "tourism", 35.0526, 129.0872, "영도", "부산 영도구 전망로", 4.6),
                busanPlace("송도해상케이블카", "tourism", 35.0764, 129.0238, "송도", "부산 서구 송도해변로", 4.6),
                busanPlace("흰여울문화마을", "tourism", 35.0787, 129.0444, "영도", "부산 영도구 영선동4가", 4.6),
                busanPlace("용두산공원 부산타워", "tourism", 35.1007, 129.0325, "남포동", "부산 중구 용두산길", 4.5),
                busanPlace("자갈치시장", "tourism", 35.0967, 129.0305, "남포동", "부산 중구 자갈치해안로", 4.4),
                busanPlace("국제시장", "tourism", 35.1023, 129.0287, "남포동", "부산 중구 국제시장2길", 4.4),
                busanPlace("부평깡통시장", "tourism", 35.1018, 129.0264, "남포동", "부산 중구 부평1길", 4.4),
                busanPlace("해동용궁사", "tourism", 35.1883, 129.2233, "기장", "부산 기장군 용궁길", 4.7),
                busanPlace("오시리아 해안산책로", "tourism", 35.1963, 129.2272, "기장", "부산 기장군 기장해안로", 4.5),
                busanPlace("동백섬", "tourism", 35.1527, 129.1521, "해운대", "부산 해운대구 우동", 4.6),
                busanPlace("더베이101", "tourism", 35.1567, 129.1520, "해운대", "부산 해운대구 동백로", 4.5),
                busanPlace("부산시립미술관", "tourism", 35.1712, 129.1370, "해운대", "부산 해운대구 APEC로", 4.5),
                busanPlace("영화의전당", "tourism", 35.1710, 129.1270, "해운대", "부산 해운대구 수영강변대로", 4.6),
                busanPlace("송정해수욕장", "tourism", 35.1786, 129.1997, "송정", "부산 해운대구 송정해변로", 4.5),
                busanPlace("다대포해수욕장", "tourism", 35.0465, 128.9667, "다대포", "부산 사하구 다대동", 4.6),
                busanPlace("부산시민공원", "tourism", 35.1683, 129.0571, "서면", "부산 부산진구 시민공원로", 4.6),
                busanPlace("범어사", "tourism", 35.2839, 129.0684, "동래", "부산 금정구 범어사로", 4.7)
        );
    }

    private List<PlaceData> buildBusanFoodPlaces() {
        return List.of(
                busanFood("해운대암소갈비집", 35.1631, 129.1667, "해운대", "고기_해산물"),
                busanFood("초량밀면", 35.1143, 129.0393, "부산역", "한식"),
                busanFood("본전돼지국밥", 35.1160, 129.0395, "부산역", "한식"),
                busanFood("할매가야밀면", 35.1516, 129.0592, "서면", "한식"),
                busanFood("동래할매파전", 35.2183, 129.0870, "동래", "한식"),
                busanFood("원조18번완당", 35.1022, 129.0307, "남포동", "한식"),
                busanFood("자갈치시장 회센터", 35.0967, 129.0305, "남포동", "고기_해산물"),
                busanFood("기장시장 대게골목", 35.2446, 129.2157, "기장", "고기_해산물"),
                busanFood("광안리 민락회타운", 35.1551, 129.1256, "광안리", "고기_해산물"),
                busanFood("송정3대국밥", 35.1581, 129.0577, "서면", "한식"),
                busanBar("광안리 해변 바거리", 35.1538, 129.1198, "광안리"),
                busanBar("해운대 포장마차촌", 35.1595, 129.1635, "해운대"),
                busanBar("전포 카페거리 펍", 35.1570, 129.0630, "서면"),
                busanBar("남포동 비프광장 포차", 35.0988, 129.0289, "남포동")
        );
    }

    private List<PlaceData> buildBusanCafePlaces() {
        return List.of(
                busanPlace("모모스커피 본점", "cafe", 35.2202, 129.0865, "동래", "부산 금정구 오시게로", 4.6),
                busanPlace("웨이브온 커피", "cafe", 35.3027, 129.2615, "기장", "부산 기장군 해맞이로", 4.5),
                busanPlace("카페 루프탑", "cafe", 35.1780, 129.1990, "송정", "부산 해운대구 송정광어골로", 4.4),
                busanPlace("흰여울비치", "cafe", 35.0785, 129.0448, "영도", "부산 영도구 절영로", 4.4),
                busanPlace("젬스톤 영도", "cafe", 35.0910, 129.0481, "영도", "부산 영도구 대교로", 4.4),
                busanPlace("에테르", "cafe", 35.0792, 129.0447, "영도", "부산 영도구 절영로", 4.4),
                busanPlace("블랙업커피 해운대", "cafe", 35.1600, 129.1615, "해운대", "부산 해운대구", 4.4),
                busanPlace("수월경화", "cafe", 35.1610, 129.1634, "해운대", "부산 해운대구", 4.4)
        );
    }

    private PlaceData busanFood(String name, double lat, double lng, String area, String foodCategory) {
        return new PlaceData(name, "food", lat, lng, 4.5, 4.5, 0, area, foodCategory,
                "부산 " + area, "", "", "");
    }

    private PlaceData busanBar(String name, double lat, double lng, String area) {
        return new PlaceData(name, "bar", lat, lng, 4.4, 4.4, 0, area, "",
                "부산 " + area, "", "", "");
    }

    private PlaceData busanPlace(String name, String category, double lat, double lng,
                                 String area, String address, double rating) {
        return new PlaceData(name, category, lat, lng, rating, rating, 0, area, "",
                address, "", "", "");
    }

    // ────────────────────────────────────────────
    //  리뷰 캐시 로딩
    // ────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void loadReviews() throws Exception {
        // reviews_food.json : [{원본이름, 평균별점, 리뷰별점, 리뷰내용}, ...]
        loadReviewFile(REVIEWS_FOOD_PATH, "원본이름", "리뷰내용", "리뷰별점", "평균별점", null, reviewCacheFood);
        // review_cafe.json  : 동일 구조
        loadReviewFile(REVIEW_CAFE_PATH,  "원본이름", "리뷰내용", "리뷰별점", "평균별점", null, reviewCacheCafe);
    }

    private void loadReviewFile(String path, String nameKey, String textKey,
                                String ratingKey, String avgKey, String authorKey,
                                Map<String, List<ReviewDto>> cache) throws Exception {
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

            cache.computeIfAbsent(name, k -> new ArrayList<>()).add(r);
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
            String rawCat    = str(m, "category");
            String cat       = "cafe".equals(defaultCategory) ? "cafe" : "바".equals(rawCat) ? "bar" : "food";
            String foodCat   = "cafe".equals(defaultCategory) ? "" : rawCat;
            String nameEn    = firstStr(m, "nameEn");    // EN JSON에만 존재
            String catEn     = firstStr(m, "categoryEn"); // EN JSON에만 존재
            if (name.isBlank() || lat == 0) continue;
            list.add(new PlaceData(name, cat, lat, lng, score, star, cnt, areaFromCoord(lat, lng), foodCat, "", "", nameEn, catEn));
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
            if (reviewsRaw instanceof List<?> revArr && !reviewCacheTourism.containsKey(name)) {
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
                if (!revList.isEmpty()) reviewCacheTourism.put(name, revList);
            }

            if (name.isBlank()) continue;
            if (lat == 0 && lng == 0) { lat = 35.1796; lng = 129.0756; }
            list.add(new PlaceData(name, "tourism", lat, lng,
                    score > 0 ? score : 1.0, star, cnt, areaFromCoord(lat, lng), "", addr, img, "", ""));
        }

        if (list.isEmpty()) {
            foreignCoverage.forEach((name, cov) -> {
                double[] coord = AREA_COORDS.getOrDefault(name, new double[]{35.1796, 129.0756});
                list.add(new PlaceData(name, "tourism", coord[0], coord[1], cov, 4.0, 0, areaFromCoord(coord[0], coord[1]), "", "", "", "", ""));
            });
        }
        return list;
    }

    // ────────────────────────────────────────────
    //  추천 + 일정 생성
    // ────────────────────────────────────────────
    public TripRecommendResponse recommend(TripRecommendRequest req) {
        boolean isEn = "en".equalsIgnoreCase(req.getLang());
        // 언어별 데이터 선택
        List<PlaceData> tourismPlaces = isEn ? tourismPlacesEn : tourismPlacesKo;
        List<PlaceData> foodSource    = isEn ? foodPlacesEn    : foodPlacesKo;
        List<PlaceData> cafeSource    = isEn ? cafePlacesEn    : cafePlacesKo;

        List<ScoredPlace> tourism = scoreAll(tourismPlaces, req);
        List<ScoredPlace> food    = scoreAll(foodSource.stream().filter(p -> !"bar".equals(p.category())).collect(Collectors.toList()), req);
        List<ScoredPlace> cafe    = scoreAll(cafeSource, req);
        List<ScoredPlace> bar     = scoreAll(foodSource.stream().filter(p -> "bar".equals(p.category())).collect(Collectors.toList()), req);

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
        boolean isEn = "en".equalsIgnoreCase(req.getLang());
        List<String> r = new ArrayList<>();
        if (req.getMustVisit() != null && req.getMustVisit().stream().anyMatch(mv -> p.name().contains(mv) || mv.contains(p.name())))
            r.add(isEn ? "Matches your must-visit list" : "꼭 가고 싶은 장소 반영");
        if (req.getPreferredAreas() != null && req.getPreferredAreas().stream().anyMatch(a -> p.area().contains(a) || p.name().contains(a) || a.contains(p.name())))
            r.add(isEn ? "Matches preferred area" : "선호 지역 우선 반영");
        if (req.getCategories() != null) {
            List<String> cats = req.getCategories();
            if (cats.contains("local"))   r.add(isEn ? "Local experience"    : "로컬 체험 요소");
            if (cats.contains("photo"))   r.add(isEn ? "Great photo spot"    : "사진/감성 포인트");
            if (cats.contains("culture")) r.add(isEn ? "Culture & history"   : "문화/역사 반영");
            if (cats.contains("nature"))  r.add(isEn ? "Nature & parks"      : "자연/공원 반영");
            if (cats.contains("healing")) r.add(isEn ? "Relaxation spot"     : "힐링 코스");
            if (cats.contains("shopping"))r.add(isEn ? "Shopping area"       : "쇼핑 반영");
            if (cats.contains("night"))   r.add(isEn ? "Nightlife pick"      : "나이트라이프 반영");
            if (cats.contains("food"))    r.add(isEn ? "Food adventure"      : "맛집 탐방");
            if (cats.contains("cafe"))    r.add(isEn ? "Café hopping"        : "카페 투어");
        }
        if (r.isEmpty()) r.add(isEn ? "Highly rated" : "기본 점수 우수");
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
        double anchorLat = 35.1796, anchorLng = 129.0756;
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
            int currentMinutes = (req.getStartHour() > 0 ? req.getStartHour() : 10) * 60;
            int endMinutes = (req.getEndHour() > 0 ? req.getEndHour() : 21) * 60;
            if (day == 1 && req.getArrivalTime() != null) {
                int readyMinutes = req.getArrivalReadyMinutes() > 0
                        ? req.getArrivalReadyMinutes() : defaultArrivalReadyMinutes(req.getArrivalAirport());
                currentMinutes = Math.max(currentMinutes,
                        req.getArrivalTime().getHour() * 60 + req.getArrivalTime().getMinute() + readyMinutes);
            }
            if (day == req.getTravelDays() && req.getDepartureTime() != null) {
                int leadMinutes = req.getDepartureLeadMinutes() > 0
                        ? req.getDepartureLeadMinutes() : defaultDepartureLeadMinutes(req.getDepartureAirport());
                endMinutes = Math.min(endMinutes,
                        req.getDepartureTime().getHour() * 60 + req.getDepartureTime().getMinute() - leadMinutes);
            }

            for (SlotOrder order : orders) {
                if (currentMinutes >= endMinutes) break;
                switch (order.getType()) {
                    case "tourism" -> {
                        ScoredPlace p = pickNext(tourism, used, anchorLat, anchorLng, 10.0);
                        if (p != null) {
                            int duration = resolveDurationMinutes(order, "tourism", p.place);
                            if (currentMinutes + duration > endMinutes) break;
                            used.add(p.place.name());
                            slots.add(makeSlot(currentMinutes, duration, "tourism", p, tourism));
                            if (p.place.lat() != 0) { anchorLat = p.place.lat(); anchorLng = p.place.lng(); }
                            currentMinutes += duration;
                        }
                    }
                    case "food" -> {
                        String fc = order.getFoodCategory();
                        List<ScoredPlace> pool = (fc != null && !fc.isBlank() && foodByCat.containsKey(fc)) ? foodByCat.get(fc) : food;
                        ScoredPlace p = pickNext(pool, used, anchorLat, anchorLng, 8.0);
                        if (p != null) {
                            int duration = resolveDurationMinutes(order, "food", p.place);
                            if (currentMinutes + duration > endMinutes) break;
                            used.add(p.place.name());
                            SlotDto slot = makeSlot(currentMinutes, duration, "food", p, pool);
                            if (fc != null && !fc.isBlank()) slot.setReason("[" + fc + "] " + slot.getReason());
                            slots.add(slot);
                            if (p.place.lat() != 0) { anchorLat = p.place.lat(); anchorLng = p.place.lng(); }
                            currentMinutes += duration;
                        }
                    }
                    case "cafe" -> {
                        ScoredPlace p = pickNext(cafe, used, anchorLat, anchorLng, 8.0);
                        if (p != null) {
                            int duration = resolveDurationMinutes(order, "cafe", p.place);
                            if (currentMinutes + duration > endMinutes) break;
                            used.add(p.place.name()); slots.add(makeSlot(currentMinutes, duration, "cafe", p, cafe)); currentMinutes += duration;
                        }
                    }
                    case "bar" -> {
                        ScoredPlace p = pickNext(bar, used, anchorLat, anchorLng, 8.0);
                        if (p != null) {
                            int duration = resolveDurationMinutes(order, "bar", p.place);
                            if (currentMinutes + duration > endMinutes) break;
                            used.add(p.place.name()); slots.add(makeSlot(currentMinutes, duration, "bar", p, bar)); currentMinutes += duration;
                        }
                    }
                }
            }
            d.setSlots(slots);
            result.add(d);
        }
        return result;
    }

    private int defaultArrivalReadyMinutes(String airport) {
        if ("PUS".equalsIgnoreCase(airport)) return 90;
        return "GMP".equalsIgnoreCase(airport) ? 120 : 180;
    }

    private int defaultDepartureLeadMinutes(String airport) {
        if ("PUS".equalsIgnoreCase(airport)) return 180;
        return "GMP".equalsIgnoreCase(airport) ? 210 : 270;
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

    private int resolveDurationMinutes(SlotOrder order, String type, PlaceData place) {
        if (order.getDurationMinutes() > 0) {
            return Math.max(30, Math.min(order.getDurationMinutes(), 8 * 60));
        }
        if (!"tourism".equals(type)) return 60;

        String text = String.join(" ",
                Optional.ofNullable(place.name()).orElse(""),
                Optional.ofNullable(place.nameEn()).orElse(""),
                Optional.ofNullable(place.categoryEn()).orElse("")).toLowerCase(Locale.ROOT);
        if (containsAny(text, "놀이공원", "테마파크", "워터파크", "동물원", "아쿠아리움",
                "amusement park", "theme park", "water park", "zoo", "aquarium")) return 360;
        if (containsAny(text, "전시관", "전시회", "박물관", "미술관", "museum", "gallery", "exhibition")) return 180;
        return 120;
    }

    private boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
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
    private SlotDto makeSlot(int startMinutes, int durationMinutes, String cat, ScoredPlace pick, List<ScoredPlace> pool) {
        PlaceData pl = pick.place;
        boolean hasEn = pl.nameEn() != null && !pl.nameEn().isBlank();
        SlotDto s = new SlotDto();
        s.setTime(String.format("%02d:%02d", startMinutes / 60, startMinutes % 60));
        s.setDurationMinutes(durationMinutes);
        // category는 항상 내부 타입 유지 (아이콘 매핑용)
        s.setCategory(cat);
        s.setPlaceName(hasEn ? pl.nameEn() : pl.name());
        // EN이고 카테고리 레이블이 있으면 reason 앞에 [Korean], [Café] 형태로 추가
        String reasonText = pick.reason;
        if (hasEn && pl.categoryEn() != null && !pl.categoryEn().isBlank()
                && !pl.categoryEn().equals("Café") && !pl.categoryEn().equals("Restaurant")) {
            reasonText = "[" + pl.categoryEn() + "] " + reasonText;
        }
        s.setReason(reasonText);
        s.setScore(Math.round(pick.score * 10000.0) / 10000.0);
        s.setLat(pl.lat());
        s.setLng(pl.lng());
        s.setAddress(pl.address());
        // imageUrl: JSON에 있으면 사용, 없으면 Unsplash 카테고리별 이미지에서 보충
        String imgUrl = pl.imageUrl();
        if (imgUrl == null || imgUrl.isBlank()) {
            String unsplashCat = resolveUnsplashCategory(cat, pl.foodCategory());
            List<String> imgs = unsplashImageService.getThumbList(unsplashCat);
            if (!imgs.isEmpty()) {
                // 장소 이름 해시로 항상 같은 이미지 선택 (랜덤하지 않게)
                imgUrl = imgs.get(Math.abs(pl.name().hashCode()) % imgs.size());
            }
        }
        s.setImageUrl(imgUrl);
        // 베이즈 별점 보정 (리뷰 수 적으면 전체 평균 쪽으로 당김)
        double correctedRating = bayesRating(pl.avgRating(), pl.reviewCount());
        s.setAvgRating(Math.round(correctedRating * 10.0) / 10.0);
        s.setReviewCount(pl.reviewCount());

        // 카테고리에 맞는 리뷰 캐시 선택 (food/cafe/tourism 분리로 이름 충돌 방지)
        boolean isEn = hasEn;
        Map<String, List<ReviewDto>> targetCache =
                "cafe".equals(cat)    ? reviewCacheCafe :
                        "tourism".equals(cat) ? reviewCacheTourism : reviewCacheFood;
        List<ReviewDto> reviews = targetCache.getOrDefault(pl.name(), new ArrayList<>());
        if (!reviews.isEmpty()) {
            s.setReviews(reviews.stream().limit(5).collect(Collectors.toList()));
        } else {
            // 리뷰 없는 경우 템플릿으로 자동 생성
            s.setReviews(generateReviews(pl.name(), pl.foodCategory(), pl.avgRating(), isEn, 5));
        }

        // 대체 후보 — EN이면 영어 이름 사용
        List<AlternativeDto> alts = pool.stream()
                .filter(sp -> !sp.place.name().equals(pl.name()))
                .limit(5)
                .map(sp -> {
                    AlternativeDto a = new AlternativeDto();
                    boolean spHasEn = sp.place.nameEn() != null && !sp.place.nameEn().isBlank();
                    a.setPlaceName(spHasEn ? sp.place.nameEn() : sp.place.name());
                    a.setScore(Math.round(sp.score * 10000.0) / 10000.0);
                    a.setLat(sp.place.lat()); a.setLng(sp.place.lng());
                    a.setDistanceKm(Math.round(haversine(pl.lat(), pl.lng(), sp.place.lat(), sp.place.lng()) * 100.0) / 100.0);
                    return a;
                }).collect(Collectors.toList());
        s.setAlternatives(alts);
        return s;
    }

    private List<RecommendedPlace> toRecList(List<ScoredPlace> scored, int limit) {
        return scored.stream().limit(limit).map(sp -> {
            boolean hasEn = sp.place.nameEn() != null && !sp.place.nameEn().isBlank();
            RecommendedPlace rp = new RecommendedPlace();
            rp.setName(hasEn ? sp.place.nameEn() : sp.place.name());
            rp.setScore(Math.round(sp.score * 10000.0) / 10000.0);
            rp.setReason(sp.reason); rp.setLat(sp.place.lat()); rp.setLng(sp.place.lng());
            rp.setAddress(sp.place.address());
            // imageUrl: JSON에 있으면 사용, 없으면 Unsplash fallback
            String recImg = sp.place.imageUrl();
            if (recImg == null || recImg.isBlank()) {
                String unsplashCat = resolveUnsplashCategory(sp.place.category(), sp.place.foodCategory());
                List<String> imgs = unsplashImageService.getThumbList(unsplashCat);
                if (!imgs.isEmpty()) {
                    recImg = imgs.get(Math.abs(sp.place.name().hashCode()) % imgs.size());
                }
            }
            rp.setImageUrl(recImg);
            rp.setAvgRating(sp.place.avgRating()); rp.setReviewCount(sp.place.reviewCount());
            return rp;
        }).collect(Collectors.toList());
    }

    public PlaceDetailDto getPlaceDetail(Long placeId) { return null; }

    /** Unsplash 카테고리 키 매핑 (UnsplashImageService.KEYWORD_MAP 키와 일치) */
    private String resolveUnsplashCategory(String slotCategory, String foodCategory) {
        if ("cafe".equals(slotCategory)) return "카페";
        if ("bar".equals(slotCategory))  return "바";
        if (foodCategory != null) {
            return switch (foodCategory) {
                case "한식"       -> "한식";
                case "일식"       -> "일식";
                case "양식"       -> "양식";
                case "중식"       -> "중식";
                case "고기_해산물" -> "고기_해산물";
                default           -> "기타";
            };
        }
        return "기타";
    }

    // ────────────────────────────────────────────
    //  유틸
    // ────────────────────────────────────────────
    private String areaFromCoord(double lat, double lng) {
        String nearest = "부산"; double minDist = Double.MAX_VALUE;
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

    // ────────────────────────────────────────────
    //  베이즈 별점 보정 (리뷰 수 적을수록 전체 평균 쪽으로)
    // ────────────────────────────────────────────
    private double bayesRating(double avg, int count) {
        final double GLOBAL_AVG = 4.1;
        final double M          = 10.0; // 가중치
        return (count * avg + M * GLOBAL_AVG) / (count + M);
    }

    // ────────────────────────────────────────────
    //  리뷰 자동 생성 (리뷰 없는 음식/카페용)
    // ────────────────────────────────────────────
    private static final Map<String, Map<Integer, List<String>>> REVIEW_TPL_KO;
    private static final Map<String, Map<Integer, List<String>>> REVIEW_TPL_EN;
    static {
        REVIEW_TPL_KO = new HashMap<>();
        REVIEW_TPL_EN = new HashMap<>();

        // ── KO 템플릿 ──
        putTpl(REVIEW_TPL_KO, "cafe", 5, List.of(
                "분위기가 정말 좋아요. 커피 맛도 훌륭하고 자리도 편안해서 오래 있기 좋아요.",
                "인테리어가 감성적이고 음료가 맛있어요. 다음에도 꼭 다시 올 것 같아요.",
                "조용하고 아늑한 분위기예요. 직원분들도 친절하고 커피 퀄리티가 높아요.",
                "동네에서 가장 마음에 드는 카페예요. 라떼가 특히 맛있고 공간도 넓어요.",
                "오래 작업하기 좋은 카페예요. 콘센트도 있고 와이파이도 잘 돼요.",
                "사진 찍기 너무 좋은 공간이에요. 음료도 맛있고 직원분 추천도 믿을 만해요.",
                "시즌 메뉴가 정말 창의적이에요. 맛도 비주얼도 완벽해요.",
                "커피 원두를 직접 로스팅하는 곳이라 신선함이 달라요. 드립 커피 강추예요.",
                "테라스에서 마시는 커피 한 잔이 최고예요. 뷰도 좋고 바람도 시원해요.",
                "베이커리와 커피 조합이 완벽해요. 스콘이 특히 맛있어서 항상 같이 주문해요."
        ));
        putTpl(REVIEW_TPL_KO, "cafe", 4, List.of(
                "커피 맛이 무난하고 공간이 깔끔해요. 가격 대비 괜찮아요.",
                "분위기는 좋은데 가끔 사람이 많아 자리 잡기 어려울 때가 있어요.",
                "음료가 맛있고 디저트도 괜찮아요. 다만 주차가 좀 불편해요.",
                "깔끔하고 아늑한 카페예요. 시그니처 메뉴가 맛있었어요.",
                "친구와 대화하기 좋은 조용한 공간이에요. 커피도 무난하게 맛있어요.",
                "가격이 합리적이고 커피 퀄리티도 꽤 좋아요. 단골 될 것 같아요.",
                "리필도 가능하고 직원이 친절해요. 오래 머물기 편한 곳이에요."
        ));
        putTpl(REVIEW_TPL_KO, "cafe", 3, List.of(
                "보통 수준의 카페예요. 특별하진 않지만 가볍게 들르기 좋아요.",
                "커피는 무난하고 공간이 좀 좁아요. 분위기는 나쁘지 않아요.",
                "접근성이 좋고 가격도 저렴해요. 맛은 평범한 편이에요.",
                "간단히 쉬어가기엔 좋아요. 커피 맛은 그냥 무난해요."
        ));
        putTpl(REVIEW_TPL_KO, "한식", 5, List.of(
                "정말 맛있어요! 특히 국물 맛이 깊고 재료가 신선해서 자꾸 생각나요.",
                "집밥 같은 따뜻한 맛이에요. 반찬도 다양하고 사장님이 정말 친절하세요.",
                "양도 푸짐하고 맛도 훌륭해요. 가격까지 합리적이라 자주 오게 돼요.",
                "정성스럽게 만든 음식이 느껴져요. 식재료 퀄리티가 높아 믿고 먹을 수 있어요.",
                "오랫동안 기억에 남는 맛이에요. 한국 음식의 진수를 보여주는 곳이에요.",
                "된장찌개 하나로 이 집 실력을 알 수 있어요. 정말 깊은 맛이 나요.",
                "밑반찬이 어쩜 이렇게 다 맛있어요. 비빔밥도 최고였어요.",
                "직접 담근 김치가 일품이에요. 재료 하나하나에서 정성이 느껴져요.",
                "점심 정식이 가성비 최고예요. 든든하게 먹고 일할 수 있어요.",
                "어머니 손맛 같은 정겨운 음식이에요. 혼자 와도 편하게 먹을 수 있는 분위기예요."
        ));
        putTpl(REVIEW_TPL_KO, "한식", 4, List.of(
                "전반적으로 맛있어요. 점심 특선이 특히 가성비 좋아요.",
                "깔끔하고 담백한 맛이에요. 혼밥하기도 좋고 서비스도 친절해요.",
                "맛은 좋은데 점심시간엔 웨이팅이 있어요. 그래도 기다릴 만해요.",
                "반찬이 알차게 구성되어 있어요. 찌개 맛도 괜찮고 분위기도 좋아요.",
                "가격 대비 양이 많고 맛도 실망스럽지 않아요. 또 오게 될 것 같아요.",
                "나물 반찬들이 싱싱하고 맛있어요. 건강한 식사를 하고 싶을 때 딱이에요."
        ));
        putTpl(REVIEW_TPL_KO, "한식", 3, List.of(
                "평범한 한식당이에요. 맛은 무난하고 가격은 적당해요.",
                "특별하진 않지만 간단히 한 끼 해결하기 좋아요.",
                "깔끔하게 먹기 좋은 곳이에요. 맛이 강하지 않아서 부담 없이 먹을 수 있어요."
        ));
        putTpl(REVIEW_TPL_KO, "일식", 5, List.of(
                "신선한 재료로 만든 음식이 입에서 살살 녹아요. 일본 현지 맛 그대로예요.",
                "셰프 실력이 느껴지는 곳이에요. 플레이팅도 예쁘고 맛도 일품이에요.",
                "라멘 국물이 진하고 깊어요. 면 탄력도 좋고 토핑도 푸짐해요.",
                "초밥 하나하나가 정성스럽게 만들어져 있어요. 재료 신선도가 확실히 달라요.",
                "오마카세 코스가 모두 훌륭했어요. 셰프님이 직접 설명해주셔서 더 맛있었어요.",
                "우동 육수가 정말 깊고 시원해요. 면발도 탱탱하고 가격도 합리적이에요.",
                "가라아게가 겉바속촉이에요. 맥주와 먹으면 완벽한 조합이에요."
        ));
        putTpl(REVIEW_TPL_KO, "일식", 4, List.of(
                "맛있고 깔끔해요. 가격이 좀 있지만 퀄리티를 생각하면 납득돼요.",
                "분위기도 좋고 음식도 맛있어요. 일식 좋아하면 추천해요.",
                "회 신선도가 좋고 가격도 적당해요. 점심 세트가 특히 가성비 좋아요.",
                "라멘 토핑이 푸짐해요. 국물이 진하고 면 삶기도 딱 맞게 해줘요."
        ));
        putTpl(REVIEW_TPL_KO, "일식", 3, List.of(
                "무난한 일식당이에요. 크게 특별하진 않지만 맛은 괜찮아요.",
                "접근성이 좋고 가격이 저렴해요. 맛은 평범한 수준이에요."
        ));
        putTpl(REVIEW_TPL_KO, "양식", 5, List.of(
                "파스타 소스가 진하고 맛있어요. 빵도 갓 구운 것 같아서 같이 먹으면 최고예요.",
                "분위기 좋고 음식도 훌륭해요. 데이트 코스로 강력 추천해요.",
                "스테이크 굽기가 딱 맞게 나와요. 와인 페어링도 좋아요.",
                "리조또가 크리미하면서도 깊은 맛이에요. 사이드 샐러드도 신선해요.",
                "셰프의 철학이 느껴지는 코스 요리예요. 모든 접시가 예술 작품 같아요.",
                "피자 도우가 얇고 바삭해요. 토핑도 아낌없이 올려줘서 한 조각에도 배불러요.",
                "브런치 메뉴가 다양하고 맛있어요. 주말 아침에 오기 딱 좋은 곳이에요."
        ));
        putTpl(REVIEW_TPL_KO, "양식", 4, List.of(
                "맛있고 플레이팅이 예뻐요. 특별한 날 방문하기 좋아요.",
                "음식 퀄리티 대비 가격이 합리적이에요. 디저트도 맛있어요.",
                "파스타 양이 넉넉하고 맛도 좋아요. 가격이 살짝 있는데 그래도 만족해요.",
                "서비스가 세심하고 음식이 빠르게 나와요. 분위기도 좋아서 또 오고 싶어요."
        ));
        putTpl(REVIEW_TPL_KO, "양식", 3, List.of(
                "무난한 양식당이에요. 맛은 괜찮고 분위기도 나쁘지 않아요.",
                "특별하진 않지만 깔끔하게 먹을 수 있어요. 가격은 적당한 편이에요."
        ));
        putTpl(REVIEW_TPL_KO, "중식", 5, List.of(
                "짜장면 소스가 깊고 진해요. 탕수육 소스도 절묘하게 맛있어요.",
                "불 맛이 살아있는 볶음 요리가 일품이에요. 마라 좋아하면 꼭 와보세요.",
                "사장님이 직접 요리하시는 게 느껴지는 맛이에요. 재료가 신선해요.",
                "짬뽕이 진하고 얼큰해서 숙취에도 최고예요. 양도 정말 많아요.",
                "딤섬 하나하나가 정성스럽게 만들어져 있어요. 향신료 조화가 완벽해요.",
                "북경오리 껍질이 바삭하고 고기도 촉촉해요. 소스와 함께 먹으면 절묘해요.",
                "깐풍기가 겉은 바삭 속은 촉촉해요. 소스가 달콤하면서도 매콤해서 자꾸 손이 가요."
        ));
        putTpl(REVIEW_TPL_KO, "중식", 4, List.of(
                "짬뽕 국물이 진하고 시원해요. 양도 충분하고 가격도 착해요.",
                "전반적으로 맛있어요. 배달보다 매장 방문이 훨씬 맛있어요.",
                "볶음밥이 고슬고슬하고 맛있어요. 기본적인 메뉴들이 다 맛있는 집이에요.",
                "양이 많아서 가성비가 좋아요. 세트 메뉴 구성도 알차요."
        ));
        putTpl(REVIEW_TPL_KO, "중식", 3, List.of(
                "무난한 중식당이에요. 깔끔하고 가격도 적당해요.",
                "맛은 평범하지만 양이 많아서 배부르게 먹을 수 있어요."
        ));
        putTpl(REVIEW_TPL_KO, "고기_해산물", 5, List.of(
                "고기 질이 정말 좋아요. 두툼하게 썰어줘서 씹는 맛이 살아있어요.",
                "신선한 해산물이 가득해요. 세트 메뉴가 구성이 좋고 가성비도 최고예요.",
                "숯불향이 살아있는 고기가 일품이에요. 밑반찬도 정갈하고 맛있어요.",
                "삼겹살이 두껍고 육즙이 풍부해요. 김치와 함께 먹으면 정말 최고예요.",
                "랍스터를 이 가격에 먹을 수 있다니 놀라워요. 신선도가 완벽해요.",
                "직원이 능숙하게 구워줘서 편하게 먹을 수 있어요. 고기 질도 최상급이에요.",
                "갈비가 연하고 달콤해요. 특제 양념이 고기에 잘 배어 있어요.",
                "새우구이가 탱글탱글하고 맛있어요. 소주 한 잔과 함께 하면 완벽해요."
        ));
        putTpl(REVIEW_TPL_KO, "고기_해산물", 4, List.of(
                "고기가 신선하고 맛있어요. 구워주시는 서비스도 감사해요.",
                "해산물 신선도가 좋아요. 가격이 좀 있지만 퀄리티에 납득해요.",
                "고기 두께가 두껍고 양이 많아요. 가족 외식으로 딱 좋은 곳이에요.",
                "굴이 정말 신선해요. 막걸리랑 먹으면 최고의 조합이에요."
        ));
        putTpl(REVIEW_TPL_KO, "고기_해산물", 3, List.of(
                "무난한 고깃집이에요. 고기 질이 나쁘지 않고 분위기도 괜찮아요.",
                "평범하지만 가격이 합리적이에요. 여럿이 먹기 좋은 구성이에요."
        ));
        putTpl(REVIEW_TPL_KO, "바", 5, List.of(
                "분위기가 너무 좋아요. 안주도 맛있고 칵테일 레시피가 독창적이에요.",
                "2차로 오기 딱 좋은 곳이에요. 루프탑에서 야경 보며 한 잔 하면 최고예요.",
                "바텐더분이 친절하게 추천해줘서 좋은 술 많이 마셨어요. 꼭 다시 올게요.",
                "크래프트 맥주 종류가 정말 다양해요. 직원이 각 맥주 특징을 잘 설명해줘요.",
                "칵테일 맛이 기대 이상이에요. 시즌 메뉴도 창의적이고 비주얼도 예뻐요.",
                "음악 선곡이 감각적이에요. 분위기 있게 한 잔 하고 싶을 때 최고예요.",
                "와인 리스트가 풍부하고 가격도 합리적이에요. 소믈리에 추천을 믿어보세요."
        ));
        putTpl(REVIEW_TPL_KO, "바", 4, List.of(
                "분위기 좋고 음악도 괜찮아요. 맥주 종류가 다양해서 좋아요.",
                "안주가 맛있고 가격이 합리적이에요. 친구들이랑 오기 좋아요.",
                "혼술하기에도 편한 분위기예요. 바텐더가 말을 걸어줘서 심심하지 않아요.",
                "생맥주 상태가 항상 좋아요. 자주 오게 되는 곳이에요."
        ));
        putTpl(REVIEW_TPL_KO, "바", 3, List.of(
                "무난한 바예요. 술 종류가 다양하고 분위기는 나쁘지 않아요.",
                "특별하진 않지만 가볍게 한 잔 하기 좋아요."
        ));
        putTpl(REVIEW_TPL_KO, "기타", 5, List.of(
                "정말 맛있어요. 다시 방문하고 싶은 곳이에요.",
                "음식 퀄리티가 높고 서비스도 좋아요. 강력 추천해요.",
                "처음 왔는데 이미 단골이 됐어요. 모든 메뉴가 믿고 먹을 수 있어요.",
                "주변에서 가장 맛있는 식당이에요. 항상 웨이팅이 있을 만해요."
        ));
        putTpl(REVIEW_TPL_KO, "기타", 4, List.of(
                "전반적으로 만족스러웠어요. 다음에 또 오고 싶어요.",
                "음식도 맛있고 서비스도 친절해요. 가격도 적당한 편이에요.",
                "분위기가 좋아서 오래 있기 편해요. 맛도 기대 이상이었어요."
        ));
        putTpl(REVIEW_TPL_KO, "기타", 3, List.of(
                "무난하게 즐길 수 있는 곳이에요.",
                "특별하진 않지만 가격이 합리적이고 음식은 기본 이상이에요."
        ));

        // ── EN 템플릿 ──
        putTpl(REVIEW_TPL_EN, "cafe", 5, List.of(
                "Love the vibe here! The coffee is excellent and the space is cozy.",
                "Beautifully designed café with great drinks. Will definitely come back.",
                "Quiet and relaxing atmosphere. Staff is friendly and coffee quality is top-notch.",
                "My favorite café in the neighborhood. Great latte and spacious seating.",
                "Perfect place to work or study. Good WiFi and plenty of outlets.",
                "The seasonal menu is so creative. Great taste and stunning presentation.",
                "They roast their own beans here — you can really taste the freshness.",
                "The terrace seating is amazing. Coffee with a great view is unbeatable.",
                "The bakery and coffee combo is perfect. The scones are a must-order.",
                "Every detail here is thoughtfully designed. This place is pure Instagram magic."
        ));
        putTpl(REVIEW_TPL_EN, "cafe", 4, List.of(
                "Decent coffee and clean space. Good value for the price.",
                "Nice atmosphere but can get crowded on weekends.",
                "Tasty drinks and decent desserts. Parking can be a bit tricky.",
                "Cozy and clean café. The signature drinks are worth trying.",
                "Good spot to catch up with friends. Coffee is reliably good.",
                "Reasonable prices and friendly staff. A solid neighborhood café.",
                "Comfortable seating and good music. I keep coming back."
        ));
        putTpl(REVIEW_TPL_EN, "cafe", 3, List.of(
                "Average café. Nothing special but a good spot for a quick break.",
                "Coffee is okay and the space is a bit small. Atmosphere is pleasant.",
                "Convenient location and affordable. Taste is on the average side.",
                "Fine for a quick stop. Don't expect anything extraordinary."
        ));
        putTpl(REVIEW_TPL_EN, "한식", 5, List.of(
                "Incredibly tasty! The broth is deep and flavorful — I keep craving it.",
                "Feels like homemade Korean food. Lots of side dishes and the owner is so kind.",
                "Generous portions and amazing flavor. Great value — I keep coming back.",
                "You can taste the quality of every ingredient. Trustworthy and delicious.",
                "One of the best Korean meals I've had in Busan. Unforgettable taste.",
                "The kimchi is house-made and absolutely divine. Goes perfectly with everything.",
                "The lunch set is incredible value. Left completely stuffed and satisfied.",
                "Bibimbap here is a work of art. Fresh vegetables and a perfectly balanced sauce.",
                "The doenjang jjigae has such a deep, rich flavor. Real home cooking.",
                "Banchan (side dishes) alone are worth the visit. Everything is freshly made."
        ));
        putTpl(REVIEW_TPL_EN, "한식", 4, List.of(
                "Overall delicious. The lunch set is especially great value.",
                "Clean, light flavors. Good for solo dining and staff is friendly.",
                "Side dishes are plentiful and the soup is comforting. Good neighborhood spot.",
                "Great for a hearty Korean meal. Portions are generous and prices are fair.",
                "Really enjoyed the galbi here. Tender and full of flavor.",
                "Solid Korean food. Nothing overly fancy but executed really well."
        ));
        putTpl(REVIEW_TPL_EN, "한식", 3, List.of(
                "Average Korean restaurant. Taste is fine and prices are reasonable.",
                "Nothing outstanding but satisfying enough for a quick meal.",
                "Simple and affordable. Good when you want something familiar and filling."
        ));
        putTpl(REVIEW_TPL_EN, "일식", 5, List.of(
                "Fresh ingredients that practically melt in your mouth. Authentic Japanese taste.",
                "You can feel the chef's skill. Beautiful plating and amazing flavor.",
                "The ramen broth is rich and deep. Great noodle texture and generous toppings.",
                "Each piece of sushi is crafted with precision. Freshness is unmatched.",
                "The omakase experience was extraordinary. Chef explained every dish beautifully.",
                "The udon broth is silky and comforting. Noodles have perfect chewiness.",
                "Karaage is golden, crispy outside, juicy inside. Perfect with a cold beer."
        ));
        putTpl(REVIEW_TPL_EN, "일식", 4, List.of(
                "Tasty and clean flavors. A bit pricey but quality justifies it.",
                "Great atmosphere and good food. Recommended for Japanese food lovers.",
                "Sashimi freshness is great and prices are fair. The lunch set is a steal.",
                "Ramen toppings are generous and broth is well-balanced. Solid choice."
        ));
        putTpl(REVIEW_TPL_EN, "일식", 3, List.of(
                "Decent Japanese restaurant. Nothing extraordinary but the food is good.",
                "Convenient and affordable. Taste is average but consistent.",
                "Fine for a quick Japanese meal. Don't expect anything beyond the basics."
        ));
        putTpl(REVIEW_TPL_EN, "양식", 5, List.of(
                "The pasta sauce is rich and delicious. Freshly baked bread makes it perfect.",
                "Great atmosphere and wonderful food. Highly recommend for a date night.",
                "Steak cooked to perfection. Great wine pairing options too.",
                "The risotto is creamy and deeply flavored. Fresh salad on the side was perfect.",
                "A chef-driven tasting menu that feels like art on a plate. Truly memorable.",
                "The pizza crust is thin and perfectly charred. Toppings are generous and high quality.",
                "Brunch menu is diverse and delicious. Perfect weekend morning spot."
        ));
        putTpl(REVIEW_TPL_EN, "양식", 4, List.of(
                "Tasty food with beautiful plating. Great for special occasions.",
                "Reasonable price for the quality. Desserts are good too.",
                "Pasta portions are hearty and flavor is great. Worth the slight premium.",
                "Attentive service and food comes out quickly. Great overall experience."
        ));
        putTpl(REVIEW_TPL_EN, "양식", 3, List.of(
                "Decent Western restaurant. Good taste and pleasant atmosphere.",
                "Nothing groundbreaking but solid execution. Good for a casual meal.",
                "Comfortable place with okay food. Prices are reasonable."
        ));
        putTpl(REVIEW_TPL_EN, "중식", 5, List.of(
                "The jjajangmyeon sauce is deep and rich. Tangsuyuk is perfectly balanced.",
                "Amazing wok-fired dishes with great smoky flavor. Must try for spice lovers.",
                "You can taste the freshness of ingredients. Feels like the chef really cares.",
                "The jjamppong has an incredible depth of flavor. Spicy, warming and satisfying.",
                "Dim sum here is hand-crafted and perfectly steamed. Exceptional quality.",
                "Peking duck with crispy skin and juicy meat. The sauce pairing is flawless.",
                "Ganjjajang is savory and complex. Best Chinese-Korean noodles I've had."
        ));
        putTpl(REVIEW_TPL_EN, "중식", 4, List.of(
                "The jjamppong broth is bold and refreshing. Great portions and fair prices.",
                "Overall delicious. Much better to dine in than delivery.",
                "Fried rice is fluffy and well-seasoned. The basics are done really well here.",
                "Great value set menus. Portions are large and flavors are satisfying."
        ));
        putTpl(REVIEW_TPL_EN, "중식", 3, List.of(
                "Decent Chinese restaurant. Clean flavors and reasonable prices.",
                "Average but fills the spot. Portions are fine and service is okay.",
                "Nothing exciting but consistent and affordable."
        ));
        putTpl(REVIEW_TPL_EN, "고기_해산물", 5, List.of(
                "Excellent quality meat. Thick cuts with amazing texture and flavor.",
                "Super fresh seafood. The set menu is well-composed and great value.",
                "Charcoal-grilled meat is phenomenal. Side dishes are clean and tasty.",
                "The samgyeopsal is thick, juicy and perfectly marbled. Goes great with kimchi.",
                "Lobster at this price is unbelievable. Freshness is absolutely impeccable.",
                "Staff grills the meat perfectly for you. Top-grade ingredients throughout.",
                "Galbi is tender and beautifully glazed. The marinade is a secret worth keeping.",
                "The grilled shrimp are plump and succulent. Perfect with a cold soju."
        ));
        putTpl(REVIEW_TPL_EN, "고기_해산물", 4, List.of(
                "Fresh and delicious meat. Love that they grill it for you.",
                "Great seafood freshness. A bit pricey but quality is worth it.",
                "Thick cuts and generous portions. Great for a family outing.",
                "Oysters are incredibly fresh. Perfect pairing with makgeolli."
        ));
        putTpl(REVIEW_TPL_EN, "고기_해산물", 3, List.of(
                "Decent BBQ place. Good quality meat and nice atmosphere.",
                "Average but enjoyable. Reasonably priced for the portions.",
                "Solid enough for a group meal. Nothing outstanding but no complaints."
        ));
        putTpl(REVIEW_TPL_EN, "바", 5, List.of(
                "Amazing vibes! Great food and creative cocktail recipes.",
                "Perfect for a second round. Rooftop with night views is unbeatable.",
                "The bartender gives great recommendations. Will definitely be back.",
                "Incredible craft beer selection. Staff explains each beer's character perfectly.",
                "Cocktails exceeded expectations. Seasonal menu is creative and visually stunning.",
                "Music curation is impeccable. The perfect place for a sophisticated evening.",
                "Wine list is extensive and fairly priced. Trust the sommelier's pairing suggestions."
        ));
        putTpl(REVIEW_TPL_EN, "바", 4, List.of(
                "Great atmosphere and good music. Wide selection of beers.",
                "Tasty snacks and reasonable prices. Great spot with friends.",
                "Comfortable for solo drinking too. Bartender is chatty and welcoming.",
                "Draft beer is always fresh and well-kept. A regular haunt now."
        ));
        putTpl(REVIEW_TPL_EN, "바", 3, List.of(
                "Decent bar. Good drink variety and pleasant atmosphere.",
                "Nothing special but a fine spot for a casual drink.",
                "Okay selection and fair prices. Gets the job done."
        ));
        putTpl(REVIEW_TPL_EN, "기타", 5, List.of(
                "Really delicious. Would love to visit again.",
                "High quality food and great service. Highly recommended.",
                "Already a regular after just one visit. Everything on the menu is trustworthy.",
                "The best restaurant in the area — the wait is always worth it."
        ));
        putTpl(REVIEW_TPL_EN, "기타", 4, List.of(
                "Overall satisfying experience. Would come back.",
                "Good food and friendly service. Reasonably priced.",
                "Nice atmosphere and tasty food. A solid all-round choice."
        ));
        putTpl(REVIEW_TPL_EN, "기타", 3, List.of(
                "A decent place to enjoy a meal.",
                "Average but does the job. Nothing to complain about.",
                "Convenient and affordable. Taste is okay."
        ));
    }

    private static void putTpl(Map<String, Map<Integer, List<String>>> map,
                               String cat, int star, List<String> texts) {
        map.computeIfAbsent(cat, k -> new HashMap<>()).put(star, texts);
    }

    /**
     * 리뷰 자동 생성 — 템플릿에서 중복 없이 최대 count개 선택
     * avgStar 기반으로 별점 구간 결정, 이름 해시로 일관된 선택 보장
     */
    private List<ReviewDto> generateReviews(String placeName, String foodCat,
                                            double avgStar, boolean isEn, int count) {
        Map<String, Map<Integer, List<String>>> tpl = isEn ? REVIEW_TPL_EN : REVIEW_TPL_KO;
        String cat = (foodCat == null || foodCat.isBlank()) ? "기타" : foodCat;
        Map<Integer, List<String>> starMap = tpl.getOrDefault(cat, tpl.getOrDefault("기타", Map.of()));

        int starKey = avgStar >= 4.5 ? 5 : avgStar >= 3.5 ? 4 : 3;
        List<String> pool = new ArrayList<>(starMap.getOrDefault(starKey, List.of()));
        // 부족하면 인접 별점에서 보충
        if (pool.size() < count) {
            pool.addAll(starMap.getOrDefault(starKey == 5 ? 4 : 5, List.of()));
        }
        if (pool.isEmpty()) return List.of();

        // 이름 해시로 셔플 → 매번 동일한 순서 보장 (중복 방지)
        Collections.shuffle(pool, new Random(placeName.hashCode()));
        List<ReviewDto> result = new ArrayList<>();
        String authorLabel = isEn ? "Visitor" : "방문객";
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            ReviewDto r = new ReviewDto();
            r.setAuthor(authorLabel + " " + (i + 1));
            r.setRating(starKey);
            r.setText(pool.get(i));
            r.setTimeDesc("");
            result.add(r);
        }
        return result;
    }
}
