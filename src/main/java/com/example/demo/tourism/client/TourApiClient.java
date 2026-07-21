package com.example.demo.tourism.client;

import com.example.demo.tourism.config.TourApiProperties;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TourApiClient {

    public static final String FOOD_CONTENT_TYPE = "39";

    private final RestClient restClient;
    private final TourApiProperties properties;
    private final ObjectMapper objectMapper;

    public TourApiClient(RestClient.Builder restClientBuilder,
                         TourApiProperties properties,
                         ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 부산 지역 관광정보를 TourAPI에서 매번 조회한다. */
    public List<TourPlaceDto> findBusanPlaces(String lang) {
        UriComponentsBuilder uri = commonUri(lang, "areaBasedList2")
                .queryParam("areaCode", properties.getBusanAreaCode())
                .queryParam("arrange", "Q")
                .queryParam("numOfRows", properties.getDefaultRows())
                .queryParam("pageNo", 1);
        return request(uri);
    }

    /** 부산으로 범위를 제한한 키워드 검색이다. */
    public List<TourPlaceDto> searchBusan(String keyword, String lang) {
        UriComponentsBuilder uri = commonUri(lang, "searchKeyword2")
                .queryParam("areaCode", properties.getBusanAreaCode())
                .queryParam("keyword", keyword)
                .queryParam("arrange", "Q")
                .queryParam("numOfRows", properties.getDefaultRows())
                .queryParam("pageNo", 1);
        return request(uri);
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
        return request(uri);
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
        detail.put("title", text(common, "title"));
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
        String baseUrl = "en".equalsIgnoreCase(lang)
                ? properties.getEnglishBaseUrl()
                : properties.getKoreanBaseUrl();
        return UriComponentsBuilder.fromUriString(baseUrl + "/" + operation)
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("MobileOS", properties.getMobileOs())
                .queryParam("MobileApp", properties.getMobileApp())
                .queryParam("_type", "json");
    }

    private List<TourPlaceDto> request(UriComponentsBuilder uriBuilder) {
        JsonNode root = requestRoot(uriBuilder);
        return mapItems(root.path("response").path("body").path("items").path("item"));
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
            case "25" -> addFields(details, intro, "distance", "taketime", "infocentertourcourse");
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

    private List<TourPlaceDto> mapItems(JsonNode items) {
        if (items.isMissingNode() || items.isNull() || items.isTextual()) return List.of();

        List<TourPlaceDto> result = new ArrayList<>();
        if (items.isArray()) {
            Iterator<JsonNode> iterator = items.elements();
            iterator.forEachRemaining(item -> addMappedItem(result, item));
        } else if (items.isObject()) {
            addMappedItem(result, items);
        }
        return result;
    }

    private void addMappedItem(List<TourPlaceDto> result, JsonNode item) {
        Double lat = nullableDouble(item, "mapy");
        Double lng = nullableDouble(item, "mapx");
        if (lat == null || lng == null) return;

        TourPlaceDto place = new TourPlaceDto();
        place.setId(text(item, "contentid"));
        place.setType(text(item, "contenttypeid"));
        place.setTitle(text(item, "title"));
        place.setAddr(joinAddress(text(item, "addr1"), text(item, "addr2")));
        place.setLat(lat);
        place.setLng(lng);
        place.setImage(normalizeImageUrl(firstNonBlank(text(item, "firstimage"), text(item, "firstimage2"))));
        place.setRating(null);
        place.setUser_ratings_total(0);
        place.setReviews(List.of());
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
