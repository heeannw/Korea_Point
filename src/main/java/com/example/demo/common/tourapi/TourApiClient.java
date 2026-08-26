package com.example.demo.common.tourapi;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 한국관광공사 관광정보 서비스 공용 클라이언트.
 *
 * <p>국문(KorService2)과 다국어 8종은 오퍼레이션·파라미터·응답 구조가 같아
 * 이 클래스 하나로 모두 처리한다. 언어별 차이(서비스 ID, 관광타입 코드)는
 * {@link TourApiLang} 이 흡수한다.
 *
 * <p><b>공모전 규정상 응답을 DB에 저장하지 않는다.</b> 대신 짧은 메모리 캐시
 * (기본 5분)로 일일 호출 한도를 지킨다. 원본 데이터 갱신 주기가 하루 1회라
 * 이 정도 캐시로 정보가 낡을 일은 없다.
 *
 * <p>API 실패 시 예외를 던지지 않고 빈 목록을 돌려준다. 홈 화면의 한 섹션이
 * 실패해도 페이지 전체가 죽지 않게 하기 위함이다.
 */
@Slf4j
@Component
public class TourApiClient {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 캐시 유지 시간 (원본 갱신 주기가 1일이라 5분이면 충분) */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    /** 캐시 항목 상한 — 무한정 쌓이는 것 방지 */
    private static final int CACHE_MAX_ENTRIES = 200;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** "https://apis.data.go.kr/B551011" — 설정값에서 서비스 ID를 떼어낸 루트 */
    private final String apiRoot;
    private final String serviceKey;
    private final String mobileOs;
    private final String mobileApp;

    public TourApiClient(
            @Value("${tourapi.base-url:https://apis.data.go.kr/B551011/KorService2}") String baseUrl,
            @Value("${tourapi.service-key:}") String serviceKey,
            @Value("${tourapi.mobile-os:ETC}") String mobileOs,
            @Value("${tourapi.mobile-app:KoreaPoint}") String mobileApp) {

        this.apiRoot = stripServiceId(baseUrl);
        this.serviceKey = serviceKey;
        this.mobileOs = mobileOs;
        this.mobileApp = mobileApp;

        if (!hasServiceKey()) {
            log.warn("TourAPI 서비스키가 없습니다. 관광 데이터 조회가 비활성화됩니다. "
                    + "(application-local.properties 의 TOUR_API_SERVICE_KEY 확인)");
        }
    }

    // ────────────────────────────────────────────────────────
    //  목록 조회
    // ────────────────────────────────────────────────────────

    /** 지역기반 관광정보 — 관광지·음식점·쇼핑 등 목록 */
    public List<TourItem> areaBasedList(TourApiQuery query) {
        return list("areaBasedList2", query, params -> {
            putIfPresent(params, "contentTypeId", query.getContentTypeId());
            putIfPresent(params, "arrange", query.getArrange());
        });
    }

    /**
     * 행사정보 — 축제·공연.
     * eventStartDate 가 없으면 오늘 날짜를 넣어 "오늘 이후" 행사를 가져온다.
     */
    public List<TourItem> searchFestival(TourApiQuery query) {
        String from = query.getEventStartDate() != null
                ? query.getEventStartDate()
                : LocalDate.now().format(YMD);

        TourApiQuery fixed = query.toBuilder().eventStartDate(from).build();

        return list("searchFestival2", fixed, params -> {
            params.put("eventStartDate", from);
            putIfPresent(params, "eventEndDate", query.getEventEndDate());
            putIfPresent(params, "arrange", query.getArrange());
        });
    }

    /** 키워드 검색 */
    public List<TourItem> searchKeyword(TourApiQuery query) {
        if (isBlank(query.getKeyword())) {
            return List.of();
        }
        return list("searchKeyword2", query, params -> {
            params.put("keyword", query.getKeyword());
            putIfPresent(params, "contentTypeId", query.getContentTypeId());
            putIfPresent(params, "arrange", query.getArrange());
        });
    }

    /** 위치기반 관광정보 — 좌표 반경 내 목록 (반경 최대 20,000m) */
    public List<TourItem> locationBasedList(TourApiQuery query) {
        if (query.getMapX() == null || query.getMapY() == null) {
            return List.of();
        }
        int radius = query.getRadius() == null ? 2000 : Math.min(query.getRadius(), 20000);

        return list("locationBasedList2", query, params -> {
            params.put("mapX", String.valueOf(query.getMapX()));
            params.put("mapY", String.valueOf(query.getMapY()));
            params.put("radius", String.valueOf(radius));
            putIfPresent(params, "contentTypeId", query.getContentTypeId());
            putIfPresent(params, "arrange", query.getArrange());
        });
    }

    // ────────────────────────────────────────────────────────
    //  내부 구현
    // ────────────────────────────────────────────────────────

    /** 목록 계열 4개 오퍼레이션의 공통 처리 — 캐시 → 호출 → 파싱 */
    private List<TourItem> list(String operation, TourApiQuery query,
                                java.util.function.Consumer<Map<String, String>> extraParams) {
        if (!hasServiceKey()) {
            return List.of();
        }

        String cacheKey = query.cacheKey(operation);
        List<TourItem> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, String> params = commonParams(query);
        extraParams.accept(params);
        putIfPresent(params, "lDongRegnCd", query.getLDongRegnCd());
        putIfPresent(params, "lDongSignguCd", query.getLDongSignguCd());
        putIfPresent(params, "lclsSystm1", query.getLclsSystm1());
        putIfPresent(params, "lclsSystm2", query.getLclsSystm2());
        putIfPresent(params, "lclsSystm3", query.getLclsSystm3());

        List<TourItem> items = fetch(operation, query.getLang(), params);
        writeCache(cacheKey, items);
        return items;
    }

    /** 모든 오퍼레이션에 공통으로 들어가는 파라미터 */
    private Map<String, String> commonParams(TourApiQuery query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("serviceKey", serviceKey);
        params.put("MobileOS", mobileOs);
        params.put("MobileApp", mobileApp);
        params.put("_type", "json");
        params.put("numOfRows", String.valueOf(Math.max(query.getNumOfRows(), 1)));
        params.put("pageNo", String.valueOf(Math.max(query.getPageNo(), 1)));
        return params;
    }

    /** 실제 HTTP 호출 + 파싱. 실패해도 예외를 밖으로 던지지 않는다. */
    private List<TourItem> fetch(String operation, String lang, Map<String, String> params) {
        String url = apiRoot + "/" + TourApiLang.serviceId(lang) + "/" + operation;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        params.forEach(builder::queryParam);

        try {
            // serviceKey 가 이미 인코딩된 문자열이라 build(true) 로 재인코딩을 막는다
            URI uri = builder.build(true).toUri();
            JsonNode root = restTemplate.getForObject(uri, JsonNode.class);
            return parseItems(root, operation);

        } catch (Exception e) {
            log.warn("TourAPI 호출 실패 [{} / {}]: {}", operation, lang, e.getMessage());
            return List.of();
        }
    }

    /** response.body.items.item[] 을 TourItem 목록으로 변환 */
    private List<TourItem> parseItems(JsonNode root, String operation) {
        if (root == null) {
            return List.of();
        }

        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asText("");
        if (!code.isEmpty() && !"0000".equals(code) && !"00".equals(code)) {
            log.warn("TourAPI 오류 [{}]: {} {}", operation, code, header.path("resultMsg").asText());
            return List.of();
        }

        JsonNode itemNode = root.path("response").path("body").path("items").path("item");
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();   // 결과 0건이면 items 가 빈 문자열로 오기도 한다
        }

        List<TourItem> result = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(node -> result.add(toItem(node)));
        } else {
            result.add(toItem(itemNode));   // 1건이면 배열이 아니라 객체로 온다
        }
        return List.copyOf(result);
    }

    private TourItem toItem(JsonNode n) {
        String addr1 = text(n, "addr1");
        String addr2 = text(n, "addr2");
        String address = isBlank(addr2) ? addr1 : (addr1 + " " + addr2).trim();

        return TourItem.builder()
                .contentId(text(n, "contentid"))
                .contentTypeId(text(n, "contenttypeid"))
                .title(text(n, "title"))
                .address(address)
                .zipcode(text(n, "zipcode"))
                .imageUrl(text(n, "firstimage"))
                .thumbnailUrl(text(n, "firstimage2"))
                .copyrightType(text(n, "cpyrhtDivCd"))
                .mapX(number(n, "mapx"))
                .mapY(number(n, "mapy"))
                .lclsSystm1(text(n, "lclsSystm1"))
                .lclsSystm2(text(n, "lclsSystm2"))
                .lclsSystm3(text(n, "lclsSystm3"))
                .createdTime(text(n, "createdtime"))
                .modifiedTime(text(n, "modifiedtime"))
                .eventStartDate(text(n, "eventstartdate"))
                .eventEndDate(text(n, "eventenddate"))
                .distance(number(n, "dist"))
                .build();
    }

    // ────────────────────────────────────────────────────────
    //  캐시
    // ────────────────────────────────────────────────────────

    private List<TourItem> readCache(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            return null;
        }
        return entry.items();
    }

    private void writeCache(String key, List<TourItem> items) {
        if (items.isEmpty()) {
            return;   // 실패했을 수도 있으므로 빈 결과는 캐시하지 않는다
        }
        if (cache.size() >= CACHE_MAX_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().isExpired());
            if (cache.size() >= CACHE_MAX_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(key, new CacheEntry(items, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    private record CacheEntry(List<TourItem> items, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    // ────────────────────────────────────────────────────────
    //  유틸
    // ────────────────────────────────────────────────────────

    public boolean hasServiceKey() {
        return !isBlank(serviceKey);
    }

    /** "…/B551011/KorService2" → "…/B551011" (언어별로 서비스 ID를 갈아끼우기 위해) */
    private static String stripServiceId(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        int lastSlash = trimmed.lastIndexOf('/');
        return lastSlash > "https://".length() ? trimmed.substring(0, lastSlash) : trimmed;
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (!isBlank(value)) {
            params.put(key, value);
        }
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText("");
        return v.isBlank() ? null : v;
    }

    private static Double number(JsonNode node, String field) {
        String v = node.path(field).asText("");
        if (v.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
