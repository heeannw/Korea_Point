package com.example.demo.tourism.client;

import com.example.demo.tourism.config.TourApiProperties;
import com.example.demo.tourism.config.TourCategoryNames;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component("tourismTourApiClient")
public class TourApiClient {

    private static final Logger log = LoggerFactory.getLogger(TourApiClient.class);

    public static final String FOOD_CONTENT_TYPE = "39";
    private static final String FESTIVAL_CONTENT_TYPE = "15";
    private static final String COURSE_CONTENT_TYPE = "25";
    private static final String SHOPPING_CONTENT_TYPE = "38";

    /**
     * 음식 분류를 화면이 쓰는 슬러그로 옮긴다. 카페도 음식점 유형 안에 들어 있다.
     *
     * 구 분류코드(cat3)는 부산 기준 63%만 채워져 있어 분류체계(lclsSystm)를 쓴다.
     * 외국식(FD02)은 중식·일식·서양식으로 잘 갈리므로 소분류를 살리고,
     * 나머지는 중분류로 묶는다. 한식 소분류(관광식당·모범음식점)는 음식 종류가 아니라
     * 인증 구분이라 그대로 쓰면 한식 대부분이 '관광식당' 하나로 보인다.
     */
    private static final Map<String, String> FOOD_CATEGORY_BY_LCLS = Map.ofEntries(
            Map.entry("FD020100", "chinese"),
            Map.entry("FD020200", "japanese"),
            Map.entry("FD020300", "western"),
            Map.entry("FD020400", "foreign"),
            Map.entry("FD020500", "fusion"),
            Map.entry("FD050100", "cafe"),
            Map.entry("FD050200", "teahouse"),
            Map.entry("FD050300", "drink"),
            Map.entry("FD01", "korean"),
            Map.entry("FD02", "foreign"),
            Map.entry("FD03", "snack"),
            Map.entry("FD04", "bar"),
            Map.entry("FD05", "cafe"));

    /** 카페 페이지로 따로 빠지는 분류들. 음식점 목록에서는 제외한다. */
    public static final Set<String> CAFE_CATEGORIES = Set.of("cafe", "teahouse", "drink");
    /** 축제는 전국을 받아 부산만 추려내므로 목록 기본값보다 넉넉히 가져온다. */
    private static final int FESTIVAL_FETCH_ROWS = 1000;
    /** 페이지를 무한정 따라가지 않도록 둔 안전장치. */
    private static final int MAX_PAGES = 20;

    /** 연관 관광지(TarRlteTarService1)는 KorService2와 인증키만 같고 호스트가 다르다. */
    private static final String TAR_RLTE_BASE_URL = "https://apis.data.go.kr/B551011/TarRlteTarService1";
    /** areaCd+signguCd 단위로만 조회되는 API라 부산 16개 구를 순회해야 한다. */
    private static final String[] BUSAN_SIGNGU_CODES = {
            "26110", "26140", "26170", "26200", "26230", "26260", "26290", "26320",
            "26350", "26380", "26410", "26440", "26470", "26500", "26530", "26710"
    };

    /**
     * 목록·상세 조회는 항상 KorService2(국문)에서 받는다.
     * 국문 외 서비스(Eng/Jpn/Chs/Fre)는 부산 데이터가 국문의 1/6~1/20 수준이라
     * (예: 음식점 519 → 19), 목록이 언어별로 크게 어긋난다.
     * 대신 국문 목록을 기준으로 두고 {@link TourPlaceI18n} 번역 오버레이(공식 번역 + 기계번역)로
     * 이름·주소를 언어별로 바꿔 끼운다.
     */
    private final RestClient restClient;
    private final TourApiProperties properties;
    private final ObjectMapper objectMapper;
    private final TourCategoryNames categoryNames;
    private final TourPlaceI18n placeI18n;

    public TourApiClient(RestClient.Builder restClientBuilder,
                         TourApiProperties properties,
                         ObjectMapper objectMapper,
                         TourCategoryNames categoryNames,
                         TourPlaceI18n placeI18n) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.categoryNames = categoryNames;
        this.placeI18n = placeI18n;
    }

    /**
     * 부산 지역 관광정보를 TourAPI에서 매번 조회한다.
     * areaBasedList2는 행사 기간을 응답에 담지 않아 종료된 축제까지 함께 내려주므로,
     * 행사만 걷어내고 searchFestival2로 오늘 기준 진행/예정 축제를 다시 채운다.
     */
    public List<TourPlaceDto> findBusanPlaces(String lang) {
        return cachedList("all|" + lang, () -> {
            UriComponentsBuilder uri = commonUri(lang, "areaBasedList2")
                    .queryParam("lDongRegnCd", properties.getBusanLegalDongCode())
                    .queryParam("arrange", "Q")
                    .queryParam("numOfRows", properties.getDefaultRows())
                    .queryParam("pageNo", 1);

            List<TourPlaceDto> places = new ArrayList<>(requestAllPages(uri, lang));
            places.removeIf(this::isFestival);
            // 여행코스는 부산에 1건뿐이라 화면에서 뺐다. 좌표 보정용 경유지 조회(건당 detailCommon2 여러 번)도 생략.
            places.removeIf(p -> COURSE_CONTENT_TYPE.equals(p.getType()));
            places.addAll(findCurrentBusanFestivals(lang));
            return withResolvedCoordinates(places);
        });
    }

    /**
     * 부산 목록은 요청마다 KorService2에 순차 호출이 여러 번 나가 느리다.
     * 언어별 결과를 짧게 캐시하고, 요청별 mutation(순위·점수·거리)이 원본을 건드리지 않도록
     * 언제나 복사본을 돌려준다.
     * ponytail: 프로세스 로컬 TTL 맵. 인스턴스가 여러 개면 각자 캐시 — 트래픽 커지면 공용 캐시로.
     */
    private static final long LIST_TTL_MS = 5 * 60_000;
    private final Map<String, Cached> listCache = new ConcurrentHashMap<>();
    private record Cached(List<TourPlaceDto> value, long expiresAt) { }

    private List<TourPlaceDto> cachedList(String key, java.util.function.Supplier<List<TourPlaceDto>> loader) {
        Cached hit = listCache.get(key);
        if (hit != null && hit.expiresAt() > System.currentTimeMillis()) return copies(hit.value());
        try {
            List<TourPlaceDto> fresh = loader.get();
            listCache.put(key, new Cached(fresh, System.currentTimeMillis() + LIST_TTL_MS));
            return copies(fresh);
        } catch (RuntimeException e) {
            // TourAPI 실패: 만료된 캐시라도 있으면 그걸로 응답한다(첫 조회 전이면 그대로 던짐).
            if (hit == null) throw e;
            log.warn("TourAPI 조회 실패, 만료 캐시로 응답 [{}]: {}", key, e.getMessage());
            return copies(hit.value());
        }
    }

    private static List<TourPlaceDto> copies(List<TourPlaceDto> src) {
        return src.stream().map(TourPlaceDto::new).collect(Collectors.toList());
    }

    /**
     * 좌표가 없는 여행코스에 경유지를 붙여 대표 좌표를 세우고,
     * 그래도 좌표를 못 채운 항목은 지도에 찍을 수 없으므로 제외한다.
     */
    private List<TourPlaceDto> withResolvedCoordinates(List<TourPlaceDto> places) {
        List<TourPlaceDto> resolved = new ArrayList<>(places);
        resolved.removeIf(place -> place.getLat() == null || place.getLng() == null);
        return resolved;
    }

    /**
     * 오늘 이후에 끝나는(진행 중이거나 예정된) 부산 축제만 조회한다.
     * 신규 등록 축제는 areacode가 비어 있어 areaCode 파라미터로 거르면 한 건도 남지 않는다.
     * 전국을 받아 법정동 시도코드로 부산을 골라내야 한다.
     */
    public List<TourPlaceDto> findCurrentBusanFestivals(String lang) {
        UriComponentsBuilder uri = commonUri(lang, "searchFestival2")
                .queryParam("eventStartDate", LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE))
                .queryParam("arrange", "A")
                .queryParam("numOfRows", FESTIVAL_FETCH_ROWS)
                .queryParam("pageNo", 1);

        JsonNode items = requestRoot(uri).path("response").path("body").path("items").path("item");
        List<TourPlaceDto> result = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                if (isBusan(item)) addMappedItem(result, item, lang);
            }
        } else if (items.isObject() && isBusan(items)) {
            addMappedItem(result, items, lang);
        }
        return result;
    }

    private boolean isBusan(JsonNode item) {
        return properties.getBusanLegalDongCode().equals(text(item, "lDongRegnCd"));
    }

    /**
     * 부산 음식점을 조회한다. TourAPI는 카페를 음식점 유형 안에 담고 있어
     * 음식점과 카페가 한 번의 호출로 함께 들어온다.
     */
    public List<TourPlaceDto> findBusanFoodPlaces(String lang) {
        return cachedList("food|" + lang, () -> {
            UriComponentsBuilder uri = commonUri(lang, "areaBasedList2")
                    .queryParam("lDongRegnCd", properties.getBusanLegalDongCode())
                    .queryParam("contentTypeId", FOOD_CONTENT_TYPE)
                    .queryParam("arrange", "Q")
                    .queryParam("numOfRows", properties.getDefaultRows())
                    .queryParam("pageNo", 1);
            return withResolvedCoordinates(requestAllPages(uri, lang));
        });
    }

    /** 부산으로 범위를 제한한 키워드 검색이다. */
    public List<TourPlaceDto> searchBusan(String keyword, String lang) {
        UriComponentsBuilder uri = commonUri(lang, "searchKeyword2")
                .queryParam("lDongRegnCd", properties.getBusanLegalDongCode())
                .queryParam("keyword", keyword)
                .queryParam("arrange", "Q")
                .queryParam("numOfRows", properties.getDefaultRows())
                .queryParam("pageNo", 1);
        return withResolvedCoordinates(dropEndedFestivals(requestAllPages(uri, lang), lang));
    }

    /** TarRlteTarService1 원본 한 줄. 응답 이름이 TourAPI contentId와 다른 체계라 매칭은 호출부(RelatedAttractionData)가 한다. */
    public record RelatedRow(String baseName, String relatedName, int rank) { }

    /**
     * 연관 관광지(다른 여행자들의 실제 방문 동선)를 부산 16개 구 전체에서 조회한다.
     * 구 하나가 실패해도 나머지는 계속 조회한다.
     */
    public List<RelatedRow> findRelatedRows() {
        requireServiceKey();
        String baseYm = resolveAvailableBaseYm();
        List<RelatedRow> rows = new ArrayList<>();
        for (String signgu : BUSAN_SIGNGU_CODES) {
            try {
                JsonNode items = requestRoot(tarRlteUri(signgu, baseYm, 1000))
                        .path("response").path("body").path("items").path("item");
                addRelatedRows(rows, items);
            } catch (Exception e) {
                log.warn("연관 관광지 조회 실패 [signgu={}]: {}", signgu, e.getMessage());
            }
        }
        return rows;
    }

    /**
     * baseYm(관광데이터랩 집계월)은 한두 달 지연되어 올라온다 — 이번 달을 그대로 넣으면 늘 0건이다.
     * 최근 달부터 거슬러 올라가며 데이터가 실제로 있는 첫 달을 찾는다.
     */
    private String resolveAvailableBaseYm() {
        for (int back = 1; back <= 4; back++) {
            String ym = LocalDate.now().minusMonths(back).format(DateTimeFormatter.ofPattern("yyyyMM"));
            try {
                int total = requestRoot(tarRlteUri(BUSAN_SIGNGU_CODES[0], ym, 1))
                        .path("response").path("body").path("totalCount").asInt(0);
                if (total > 0) return ym;
            } catch (Exception ignored) { /* 다음 달로 계속 */ }
        }
        return LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private UriComponentsBuilder tarRlteUri(String signgu, String baseYm, int numOfRows) {
        return UriComponentsBuilder.fromUriString(TAR_RLTE_BASE_URL + "/areaBasedList1")
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("MobileOS", properties.getMobileOs())
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", "json")
                .queryParam("numOfRows", numOfRows)
                .queryParam("pageNo", 1)
                .queryParam("areaCd", properties.getBusanLegalDongCode())
                .queryParam("signguCd", signgu)
                .queryParam("baseYm", baseYm);
    }

    private void addRelatedRows(List<RelatedRow> rows, JsonNode items) {
        if (items.isArray()) {
            for (JsonNode item : items) addRelatedRow(rows, item);
        } else if (items.isObject()) {
            addRelatedRow(rows, items);
        }
    }

    /** '관광지' 대분류만 남긴다(음식·숙박 등은 연관 관광지 섹션에서 제외). */
    private void addRelatedRow(List<RelatedRow> rows, JsonNode item) {
        if (!"관광지".equals(text(item, "rlteCtgryLclsNm"))) return;
        String base = text(item, "tAtsNm");
        String related = text(item, "rlteTatsNm");
        if (base == null || related == null) return;
        int rank;
        try { rank = Integer.parseInt(text(item, "rlteRank")); } catch (Exception e) { rank = 999; }
        rows.add(new RelatedRow(base, related, rank));
    }

    /** 좌표 주변 장소를 조회한다. radiusKm는 TourAPI 제한에 맞춰 최대 20km로 제한한다. */
    public List<TourPlaceDto> findNearby(double lat, double lng, double radiusKm,
                                         String contentTypeId, String lang) {
        int radiusMeters = (int) Math.round(Math.max(1.0, Math.min(radiusKm, 20.0)) * 1000.0);
        UriComponentsBuilder uri = commonUri(lang, "locationBasedList2")
                .queryParam("mapX", lng)
                .queryParam("mapY", lat)
                .queryParam("radius", radiusMeters)
                .queryParam("arrange", "E")
                .queryParam("numOfRows", properties.getDefaultRows())
                .queryParam("pageNo", 1);
        if (contentTypeId != null && !contentTypeId.isBlank()) {
            uri.queryParam("contentTypeId", contentTypeId);
        }
        return dropEndedFestivals(request(uri, lang), lang);
    }

    /**
     * 저장(찜) 스냅샷용. detailCommon2에서 국문 이름·주소·좌표·이미지를 그대로 담는다.
     * 번역 오버레이는 적용하지 않는다(플래너가 국문 이름으로 매칭하기 때문).
     * 조회 실패 시 null.
     */
    public TourPlaceDto findKoreanSnapshot(String contentId) {
        JsonNode item = firstItem(requestRoot(
                commonUri("ko", "detailCommon2").queryParam("contentId", contentId)));
        TourPlaceDto place = new TourPlaceDto();
        place.setId(text(item, "contentid"));
        if (place.getId() == null) return null;
        place.setType(text(item, "contenttypeid"));
        place.setTitle(text(item, "title"));
        place.setAddr(joinAddress(text(item, "addr1"), text(item, "addr2")));
        place.setLat(coordinate(item, "mapy"));
        place.setLng(coordinate(item, "mapx"));
        place.setImage(normalizeImageUrl(
                firstNonBlank(text(item, "firstimage"), text(item, "firstimage2"))));
        if (FOOD_CONTENT_TYPE.equals(place.getType())) {
            place.setFoodCategory(resolveFoodCategory(item));
        }
        return place;
    }

    /** 상세 패널에서 사용할 설명과 콘텐츠 유형별 운영정보를 조회한다. */
    public Map<String, Object> findDetail(String contentId, String contentTypeId, String lang) {
        UriComponentsBuilder commonUri = commonUri(lang, "detailCommon2")
                .queryParam("contentId", contentId);

        UriComponentsBuilder introUri = commonUri(lang, "detailIntro2")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId);

        UriComponentsBuilder imageUri = commonUri(lang, "detailImage2")
                .queryParam("contentId", contentId)
                .queryParam("numOfRows", 30)
                .queryParam("pageNo", 1);

        JsonNode common = firstItem(requestRoot(commonUri));
        JsonNode intro = firstItem(requestRoot(introUri));
        JsonNode imageItems = requestRoot(imageUri)
                .path("response").path("body").path("items").path("item");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contentId", contentId);
        detail.put("contentTypeId", contentTypeId);
        // 상세도 KorService2에서 받으므로 제목은 번역 오버레이로 맞춘다. 개요는 v1에서 국문 유지.
        String koreanTitle = text(common, "title");
        detail.put("title", placeI18n.translateTitle(lang, contentId, koreanTitle));
        detail.put("overview", text(common, "overview"));
        detail.put("homepage", text(common, "homepage"));
        detail.put("tel", text(common, "tel"));
        String representativeImage = normalizeImageUrl(
                firstNonBlank(text(common, "firstimage"), text(common, "firstimage2")));
        detail.put("image", representativeImage);
        detail.put("images", imageUrls(imageItems, representativeImage));
        detail.put("details", introDetails(intro, contentTypeId));
        return detail;
    }

    private UriComponentsBuilder commonUri(String lang, String operation) {
        requireServiceKey();
        // lang 과 무관하게 KorService2 만 쓴다. 번역은 TourPlaceI18n 오버레이가 담당한다.
        String baseUrl = properties.getKoreanBaseUrl();
        return UriComponentsBuilder.fromUriString(baseUrl + "/" + operation)
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("MobileOS", properties.getMobileOs())
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", "json");
    }

    private List<TourPlaceDto> request(UriComponentsBuilder uriBuilder, String lang) {
        JsonNode root = requestRoot(uriBuilder);
        return mapItems(root.path("response").path("body").path("items").path("item"), lang);
    }

    /**
     * totalCount에 도달할 때까지 페이지를 이어 받는다.
     * 부산 관광정보는 700건에 가까워 numOfRows 한 페이지로는 목록 뒤쪽이 통째로 잘린다.
     */
    private List<TourPlaceDto> requestAllPages(UriComponentsBuilder uriBuilder, String lang) {
        int pageSize = Math.max(1, properties.getDefaultRows());
        List<TourPlaceDto> all = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = requestRoot(uriBuilder.replaceQueryParam("pageNo", page))
                    .path("response").path("body");
            all.addAll(mapItems(body.path("items").path("item"), lang));
            if ((long) page * pageSize >= body.path("totalCount").asLong(0)) break;
        }
        return all;
    }

    private boolean isFestival(TourPlaceDto place) {
        return FESTIVAL_CONTENT_TYPE.equals(place.getType());
    }

    /**
     * 목록에 섞여 온 종료된 축제를 걸러낸다. 검색/주변 조회 응답에는 행사 기간이 없어
     * 현재 진행 중인 축제 목록과 대조하는 방식으로 판별한다.
     */
    private List<TourPlaceDto> dropEndedFestivals(List<TourPlaceDto> places, String lang) {
        if (places.stream().noneMatch(this::isFestival)) return places;

        Set<String> ongoing = findCurrentBusanFestivals(lang).stream()
                .map(TourPlaceDto::getId)
                .collect(Collectors.toSet());

        List<TourPlaceDto> filtered = new ArrayList<>(places);
        filtered.removeIf(place -> isFestival(place) && !ongoing.contains(place.getId()));
        return filtered;
    }

    private JsonNode requestRoot(UriComponentsBuilder uriBuilder) {
        URI uri = uriBuilder.build().encode().toUri();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode header = root.path("response").path("header");
            String resultCode = header.path("resultCode").asText();
            if (!"0000".equals(resultCode)) {
                throw new TourApiException("TourAPI 오류 " + resultCode + ": "
                        + header.path("resultMsg").asText("알 수 없는 오류"));
            }
            return root;
        } catch (TourApiException e) {
            throw e;
        } catch (Exception e) {
            throw new TourApiException("TourAPI 호출 또는 응답 처리에 실패했습니다.", e);
        }
    }

    private JsonNode firstItem(JsonNode root) {
        JsonNode item = root.path("response").path("body").path("items").path("item");
        if (item.isArray()) return item.isEmpty() ? objectMapper.createObjectNode() : item.get(0);
        return item.isObject() ? item : objectMapper.createObjectNode();
    }

    private Map<String, String> introDetails(JsonNode intro, String contentTypeId) {
        Map<String, String> details = new LinkedHashMap<>();
        switch (contentTypeId == null ? "" : contentTypeId) {
            case "12" -> addFields(details, intro,
                    "usetime", "restdate", "infocenter", "parking");
            case "14" -> addFields(details, intro,
                    "usetimeculture", "restdateculture", "usefee", "parkingculture");
            case "15" -> addFields(details, intro,
                    "eventstartdate", "eventenddate", "eventplace", "playtime",
                    "usetimefestival", "spendtimefestival");
            // theme은 '----지자체-----' 같은 플레이스홀더가 그대로 들어오는 경우가 많아 제외한다.
            case "25" -> addFields(details, intro,
                    "distance", "schedule", "taketime", "infocentertourcourse");
            case "28" -> addFields(details, intro,
                    "openperiod", "usetimeleports", "restdateleports", "reservation",
                    "expagerangeleports", "usefeeleports");
            case "32" -> addFields(details, intro,
                    "checkintime", "checkouttime", "reservationurl", "roomcount", "parkinglodging");
            case "38" -> addFields(details, intro,
                    "opentime", "restdateshopping", "saleitem", "parking");
            case "39" -> addFields(details, intro,
                    "firstmenu", "opentimefood", "restdatefood", "reservationfood", "treatmenu");
            default -> { }
        }
        return details;
    }

    private void addFields(Map<String, String> target, JsonNode source, String... fields) {
        for (String field : fields) {
            String value = text(source, field);
            if (value != null && !value.isBlank()) target.put(field, value);
        }
    }

    private List<TourPlaceDto> mapItems(JsonNode items, String lang) {
        if (items.isMissingNode() || items.isNull() || items.isTextual()) return List.of();

        List<TourPlaceDto> result = new ArrayList<>();
        if (items.isArray()) {
            Iterator<JsonNode> iterator = items.elements();
            iterator.forEachRemaining(item -> addMappedItem(result, item, lang));
        } else if (items.isObject()) {
            addMappedItem(result, items, lang);
        }
        return result;
    }

    private void addMappedItem(List<TourPlaceDto> result, JsonNode item, String lang) {
        Double lat = coordinate(item, "mapy");
        Double lng = coordinate(item, "mapx");
        String type = text(item, "contenttypeid");

        // 여행코스는 대표 좌표가 없으므로 경유지로 채울 수 있게 일단 통과시킨다.
        if ((lat == null || lng == null) && !COURSE_CONTENT_TYPE.equals(type)) return;
        if (isUntaggedShop(type, item)) return;

        TourPlaceDto place = new TourPlaceDto();
        place.setId(text(item, "contentid"));
        place.setType(type);
        place.setTitle(text(item, "title"));
        place.setAddr(joinAddress(text(item, "addr1"), text(item, "addr2")));
        place.setLat(lat);
        place.setLng(lng);
        place.setImage(normalizeImageUrl(firstNonBlank(text(item, "firstimage"), text(item, "firstimage2"))));
        place.setTypeName(resolveTypeName(item, lang));
        place.setTypeDetailName(categoryNames.resolve(lang,
                text(item, "lclsSystm3"), text(item, "lclsSystm2"), text(item, "lclsSystm1")));
        if (FOOD_CONTENT_TYPE.equals(type)) {
            place.setFoodCategory(resolveFoodCategory(item));
        }
        if (FESTIVAL_CONTENT_TYPE.equals(type)) {
            place.setEventStartDate(text(item, "eventstartdate"));
            place.setEventEndDate(text(item, "eventenddate"));
        }
        place.setRating(null);
        place.setUser_ratings_total(0);
        place.setReviews(List.of());

        // 국문 목록을 기준으로 받았으므로, 국문 외 언어는 이름·주소를 번역 오버레이로 바꿔 끼운다.
        placeI18n.apply(lang, place);

        result.add(place);
    }

    private void requireServiceKey() {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) {
            throw new TourApiException(
                    "TOUR_API_SERVICE_KEY가 없습니다. 공공데이터포털 일반 인증키(Decoding)를 환경변수로 설정하세요.");
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    /** 소분류를 먼저 보고 없으면 중분류로 물러난다. 둘 다 못 찾으면 기타로 둔다. */
    private String resolveFoodCategory(JsonNode item) {
        String small = text(item, "lclsSystm3");
        if (small != null && FOOD_CATEGORY_BY_LCLS.containsKey(small)) {
            return FOOD_CATEGORY_BY_LCLS.get(small);
        }
        String medium = text(item, "lclsSystm2");
        return medium == null ? "etc" : FOOD_CATEGORY_BY_LCLS.getOrDefault(medium, "etc");
    }

    /**
     * 화면에 띄울 세부 타입명이다.
     * 한식은 소분류가 '관광식당'·'모범음식점' 같은 인증 구분이라 중분류('한식')를 쓴다.
     */
    private String resolveTypeName(JsonNode item, String lang) {
        String medium = text(item, "lclsSystm2");
        String small = "FD01".equals(medium) ? null : text(item, "lclsSystm3");
        return categoryNames.resolve(lang, small, medium, text(item, "lclsSystm1"));
    }

    /**
     * 분류 정보가 전혀 없는 쇼핑 항목만 걸러낸다.
     * 예전에는 구 분류코드(cat3)로 판단해 cat3가 붙은 곳만 남겼으나,
     * 국문 외 서비스에는 cat3가 거의 비어 있어 면세점·전통시장까지 통째로 사라졌다.
     * 이제 lclsSystm(분류체계, 100% 채워짐)이 있으면 통과시켜 언어별 목록을 맞춘다.
     * 다른 유형은 분류코드가 비어도 정상 콘텐츠이므로(해수욕장이 대표적) 이 규칙을 적용하지 않는다.
     */
    private boolean isUntaggedShop(String type, JsonNode item) {
        if (!SHOPPING_CONTENT_TYPE.equals(type)) return false;
        boolean classified = text(item, "cat3") != null
                || text(item, "lclsSystm2") != null
                || text(item, "lclsSystm3") != null;
        return !classified;
    }

    /** TourAPI는 좌표가 없는 항목에 빈 값이 아니라 0을 넣어 보낸다. */
    private Double coordinate(JsonNode node, String field) {
        Double value = nullableDouble(node, field);
        return value == null || value == 0.0 ? null : value;
    }

    private Double nullableDouble(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) return null;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String joinAddress(String first, String second) {
        if (first == null) return second;
        if (second == null) return first;
        return first + " " + second;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private List<String> imageUrls(JsonNode items, String representativeImage) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        if (representativeImage != null) urls.add(representativeImage);
        if (items.isArray()) {
            for (JsonNode item : items) addImageUrl(urls, item);
        } else if (items.isObject()) {
            addImageUrl(urls, items);
        }
        return List.copyOf(urls);
    }

    private void addImageUrl(Set<String> urls, JsonNode item) {
        String url = normalizeImageUrl(
                firstNonBlank(text(item, "originimgurl"), text(item, "smallimageurl")));
        if (url != null) urls.add(url);
    }

    private String normalizeImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }
}
