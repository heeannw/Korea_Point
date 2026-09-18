package com.example.demo.reservation.service;

import com.example.demo.common.tourapi.TourApiLang;
import com.example.demo.reservation.dto.TourApiPlaceDto;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiReservationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Set<String> unavailableServices = ConcurrentHashMap.newKeySet();
    private final TourPlaceI18n placeI18n;
    private final ReservationDetailTranslationService detailTranslationService;

    @Value("${tourapi.base-url}")
    private String baseUrl;

    @Value("${tourapi.service-key:}")
    private String serviceKey;

    @Value("${tourapi.mobile-os:ETC}")
    private String mobileOs;

    @Value("${tourapi.mobile-app:KoreaPoint}")
    private String mobileApp;

    @Value("${tourapi.area-code:6}")
    private String areaCode;

    @Value("${tourapi.list-path:/areaBasedList1}")
    private String listPath;

    @Value("${tourapi.detail-path:/detailCommon1}")
    private String detailPath;

    public List<TourApiPlaceDto> findPlaces(String keyword, String contentTypeId, String lang,
                                            int pageNo, int numOfRows) {
        if (!hasServiceKey()) {
            return List.of();
        }

        String localizedContentTypeId = koreanContentTypeId(contentTypeId);
        if ("unsupported".equals(localizedContentTypeId)) {
            return List.of();
        }

        if (keyword != null && !keyword.isBlank()) {
            return searchPlaces(keyword, contentTypeId, lang, pageNo, numOfRows);
        }

        UriComponentsBuilder builder = commonBuilder(baseUrl + listPath)
                .queryParam("pageNo", Math.max(pageNo, 1))
                .queryParam("numOfRows", Math.max(numOfRows, 1))
                .queryParam("arrange", "A")
                .queryParam("areaCode", areaCode);

        if (localizedContentTypeId != null && !localizedContentTypeId.isBlank()) {
            builder.queryParam("contentTypeId", localizedContentTypeId);
        }

        URI uri = builder.build(true).toUri();

        JsonNode root = request(uri);
        List<TourApiPlaceDto> koreanPlaces = parseItems(root, TourApiLang.KO);
        List<TourApiPlaceDto> places = isKorean(lang)
                ? koreanPlaces : localizePlaces(koreanPlaces, contentTypeId, lang, pageNo, numOfRows);
        return places;
    }

    private List<TourApiPlaceDto> searchPlaces(String keyword, String contentTypeId, String lang,
                                              int pageNo, int numOfRows) {
        String normalized = keyword.trim().toLowerCase(Locale.ROOT);
        int pageSize = Math.max(numOfRows, 1);
        long skip = (long) (Math.max(pageNo, 1) - 1) * pageSize;
        int batchSize = 100;
        List<TourApiPlaceDto> matches = new ArrayList<>();
        // Filter before pagination: a nonmatching source page must not end the search.
        for (int sourcePage = 1; ; sourcePage++) {
            List<TourApiPlaceDto> batch = findPlaces(null, contentTypeId, lang, sourcePage, batchSize);
            for (TourApiPlaceDto place : batch) {
                if (!contains(place.getTitle(), normalized) && !contains(place.getAddress(), normalized)) continue;
                if (skip > 0) {
                    skip--;
                } else {
                    matches.add(place);
                    if (matches.size() == pageSize) return matches;
                }
            }
            if (batch.size() < batchSize) return matches;
        }
    }

    public TourApiPlaceDto findDetail(String contentId, String contentTypeId, String lang) {
        if (!hasServiceKey() || contentId == null || contentId.isBlank()) {
            return null;
        }

        // KorService2의 detailCommon2는 contentId만 전달하면 상세 필드를 반환한다.
        // 구형 detailCommon1용 YN/contentTypeId 파라미터를 함께 보내면 일부 항목이 빈 응답이 된다.
        UriComponentsBuilder builder = commonBuilder(baseUrl + detailPath)
                .queryParam("contentId", contentId);

        JsonNode commonItem = firstItem(request(builder.build(true).toUri()));
        if (commonItem == null) return null;

        String resolvedContentTypeId = contentTypeId;
        if (resolvedContentTypeId == null || resolvedContentTypeId.isBlank()) {
            resolvedContentTypeId = text(commonItem, "contenttypeid");
        }
        Map<String, String> operatingInformation = findOperatingInformation(
                contentId, resolvedContentTypeId, TourApiLang.KO);
        if (isKorean(lang)) {
            return toPlace(commonItem, TourApiLang.KO, text(commonItem, "overview"),
                    localizeOperatingLabels(operatingInformation, TourApiLang.KO));
        }

        JsonNode officialItem = firstItem(request(commonBuilder(apiBase(lang) + detailPath)
                .queryParam("contentId", contentId).build(true).toUri()));
        String officialType = foreignContentTypeId(resolvedContentTypeId, lang);
        Map<String, String> officialDetails = officialItem == null ? Map.of()
                : findOperatingInformation(contentId, officialType, lang);
        String koreanOverview = text(commonItem, "overview");
        String officialOverview = officialItem == null ? "" : text(officialItem, "overview");
        Map<String, String> missingDetails = new LinkedHashMap<>();
        operatingInformation.forEach((key, value) -> {
            if (!officialDetails.containsKey(key) || officialDetails.get(key).isBlank()) missingDetails.put(key, value);
        });
        ReservationDetailTranslationService.TranslationResult fallback = detailTranslationService.translate(
                contentId, lang, officialOverview.isBlank() ? koreanOverview : "", missingDetails);
        Map<String, String> mergedDetails = new LinkedHashMap<>(fallback.operatingInformation());
        mergedDetails.putAll(officialDetails);
        TourApiPlaceDto korean = toPlace(commonItem, TourApiLang.KO,
                officialOverview.isBlank() ? fallback.overview() : officialOverview, mergedDetails);
        return mergeOfficial(korean, officialItem == null ? null : toPlace(officialItem, TourApiLang.KO), lang);
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    private String koreanContentTypeId(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return "";
        }
        String logicalType = switch (requestedType) {
            case "tourist_spot" -> TourApiLang.TOURIST_SPOT;
            case "culture" -> TourApiLang.CULTURE;
            case "festival" -> TourApiLang.FESTIVAL;
            case "course" -> TourApiLang.COURSE;
            case "leports" -> TourApiLang.LEPORTS;
            case "lodging" -> TourApiLang.LODGING;
            case "shopping" -> TourApiLang.SHOPPING;
            case "restaurant" -> TourApiLang.RESTAURANT;
            case "transport" -> TourApiLang.TRANSPORT;
            default -> null;
        };
        if (logicalType == null) {
            return requestedType;
        }
        String localized = TourApiLang.contentTypeId(TourApiLang.KO, logicalType);
        return localized == null ? "unsupported" : localized;
    }

    private UriComponentsBuilder commonBuilder(String url) {
        return UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json");
    }

    private JsonNode request(URI uri) {
        String serviceId = serviceIdOf(uri);
        if (unavailableServices.contains(serviceId)) {
            return null;
        }
        try {
            return restTemplate.getForObject(uri, JsonNode.class);
        } catch (HttpClientErrorException.Forbidden e) {
            if (e.getResponseBodyAsString().contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")) {
                unavailableServices.add(serviceId);
                log.warn("TourAPI service is not approved for the configured key: {}. Falling back to cached/DeepL translation.",
                        serviceId);
                return null;
            }
            log.warn("TourAPI request forbidden: {} (status={})", safeUri(uri), e.getStatusCode());
            return null;
        } catch (RestClientException e) {
            log.warn("TourAPI request failed: {} ({})", safeUri(uri), e.getClass().getSimpleName());
            return null;
        }
    }

    private String serviceIdOf(URI uri) {
        String path = uri.getPath();
        if (path == null) return "unknown";
        for (String serviceId : List.of("KorService2", "EngService2", "JpnService2", "ChsService2",
                "ChtService2", "GerService2", "FreService2", "SpnService2", "RusService2")) {
            if (path.contains("/" + serviceId + "/")) return serviceId;
        }
        return "unknown";
    }

    private String safeUri(URI uri) {
        return UriComponentsBuilder.fromUri(uri)
                .replaceQueryParam("serviceKey", "***")
                .build(true)
                .toUriString();
    }

    private List<TourApiPlaceDto> parseItems(JsonNode root, String lang) {
        JsonNode itemNode = root == null ? null : root.at("/response/body/items/item");
        if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }

        List<TourApiPlaceDto> places = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(item -> places.add(toPlace(item, lang)));
        } else if (itemNode.isObject()) {
            places.add(toPlace(itemNode, lang));
        }
        return places;
    }

    private TourApiPlaceDto toPlace(JsonNode item, String lang) {
        return toPlace(item, lang, text(item, "overview"), Map.of());
    }

    private TourApiPlaceDto toPlace(JsonNode item, String lang, String overview,
                                    Map<String, String> operatingInformation) {
        String address = text(item, "addr1");
        String addr2 = text(item, "addr2");
        if (!addr2.isBlank()) {
            address = address.isBlank() ? addr2 : address + " " + addr2;
        }

        String title = text(item, "title");
        String imageUrl = firstText(item, "firstimage", "firstimage2");
        TourPlaceDto translated = new TourPlaceDto();
        translated.setId(text(item, "contentid"));
        translated.setTitle(title);
        translated.setAddr(address);
        placeI18n.apply(overlayLanguage(lang), translated);

        return TourApiPlaceDto.builder()
                .contentId(text(item, "contentid"))
                .contentTypeId(text(item, "contenttypeid"))
                .title(translated.getTitle())
                .address(translated.getAddr())
                .tel(text(item, "tel"))
                .imageUrl(imageUrl)
                .mapX(text(item, "mapx"))
                .mapY(text(item, "mapy"))
                .overview(overview)
                .operatingInformation(operatingInformation)
                .build();
    }

    private Map<String, String> findOperatingInformation(String contentId, String contentTypeId, String lang) {
        if (contentTypeId == null || contentTypeId.isBlank()) return Map.of();
        UriComponentsBuilder introBuilder = commonBuilder(apiBase(lang) + "/detailIntro2")
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId);
        JsonNode intro = firstItem(request(introBuilder.build(true).toUri()));
        if (intro == null) return Map.of();

        Map<String, String> details = new LinkedHashMap<>();
        switch (contentTypeId) {
            case "12" -> addFields(details, intro, "usetime", "restdate", "infocenter", "parking");
            case "14" -> addFields(details, intro, "usetimeculture", "restdateculture", "usefee", "parkingculture");
            case "15" -> addFields(details, intro, "eventstartdate", "eventenddate", "eventplace", "playtime", "usetimefestival", "spendtimefestival");
            case "25" -> addFields(details, intro, "distance", "schedule", "taketime", "infocentertourcourse");
            case "28" -> addFields(details, intro, "openperiod", "usetimeleports", "restdateleports", "reservation", "expagerangeleports", "usefeeleports");
            case "32" -> addFields(details, intro, "checkintime", "checkouttime", "reservationurl", "roomcount", "parkinglodging");
            case "38" -> addFields(details, intro, "opentime", "restdateshopping", "saleitem", "parking");
            case "39", "82" -> addFields(details, intro, "firstmenu", "opentimefood", "restdatefood", "reservationfood", "treatmenu");
            case "76" -> addFields(details, intro, "usetime", "restdate", "infocenter", "parking");
            case "78" -> addFields(details, intro, "usetimeculture", "restdateculture", "usefee", "parkingculture");
            case "85" -> addFields(details, intro, "eventstartdate", "eventenddate", "eventplace", "playtime", "usetimefestival", "spendtimefestival");
            case "75" -> addFields(details, intro, "openperiod", "usetimeleports", "restdateleports", "reservation", "expagerangeleports", "usefeeleports");
            case "80" -> addFields(details, intro, "checkintime", "checkouttime", "reservationurl", "roomcount", "parkinglodging");
            case "79" -> addFields(details, intro, "opentime", "restdateshopping", "saleitem", "parking");
            default -> { }
        }
        return details;
    }

    private List<TourApiPlaceDto> localizePlaces(List<TourApiPlaceDto> koreanPlaces, String requestedType,
                                                  String lang, int pageNo, int numOfRows) {
        String foreignType = requestedContentTypeId(requestedType, lang);
        if ("unsupported".equals(foreignType)) {
            return koreanPlaces.stream().map(place -> mergeOfficial(place, null, lang)).toList();
        }
        UriComponentsBuilder builder = commonBuilder(apiBase(lang) + listPath)
                .queryParam("pageNo", Math.max(pageNo, 1))
                .queryParam("numOfRows", Math.max(numOfRows, 1))
                .queryParam("arrange", "A")
                .queryParam("areaCode", areaCode);
        if (foreignType != null && !foreignType.isBlank()) builder.queryParam("contentTypeId", foreignType);
        Map<String, TourApiPlaceDto> officialById = parseItems(request(builder.build(true).toUri()), TourApiLang.KO)
                .stream().collect(Collectors.toMap(TourApiPlaceDto::getContentId, Function.identity(), (a, b) -> a));
        return koreanPlaces.stream()
                .map(place -> mergeOfficial(place, officialById.get(place.getContentId()), lang))
                .toList();
    }

    private TourApiPlaceDto mergeOfficial(TourApiPlaceDto korean, TourApiPlaceDto official, String lang) {
        TourPlaceDto fallback = new TourPlaceDto();
        fallback.setId(korean.getContentId());
        fallback.setTitle(korean.getTitle());
        fallback.setAddr(korean.getAddress());
        placeI18n.apply(overlayLanguage(lang), fallback);
        return TourApiPlaceDto.builder()
                .contentId(korean.getContentId()).contentTypeId(korean.getContentTypeId())
                .title(nonBlank(official == null ? null : official.getTitle(), fallback.getTitle()))
                .address(nonBlank(official == null ? null : official.getAddress(), fallback.getAddr()))
                .tel(nonBlank(official == null ? null : official.getTel(), korean.getTel()))
                .imageUrl(nonBlank(official == null ? null : official.getImageUrl(), korean.getImageUrl()))
                .mapX(korean.getMapX()).mapY(korean.getMapY()).overview(korean.getOverview())
                .operatingInformation(localizeOperatingLabels(korean.getOperatingInformation(), lang)).build();
    }

    private String requestedContentTypeId(String requestedType, String lang) {
        String logical = logicalType(requestedType);
        if (logical == null) return requestedType == null ? "" : requestedType;
        String value = TourApiLang.contentTypeId(lang, logical);
        return value == null ? "unsupported" : value;
    }

    private String foreignContentTypeId(String koreanType, String lang) {
        String logical = logicalType(koreanType);
        if (logical == null) return koreanType;
        return TourApiLang.contentTypeId(lang, logical);
    }

    private String logicalType(String type) {
        if (type == null || type.isBlank()) return null;
        return switch (type) {
            case "tourist_spot", "12" -> TourApiLang.TOURIST_SPOT;
            case "culture", "14" -> TourApiLang.CULTURE;
            case "festival", "15" -> TourApiLang.FESTIVAL;
            case "course", "25" -> TourApiLang.COURSE;
            case "leports", "28" -> TourApiLang.LEPORTS;
            case "lodging", "32" -> TourApiLang.LODGING;
            case "shopping", "38" -> TourApiLang.SHOPPING;
            case "restaurant", "39" -> TourApiLang.RESTAURANT;
            default -> null;
        };
    }

    private String apiBase(String lang) {
        int slash = baseUrl.lastIndexOf('/');
        return (slash < 0 ? baseUrl : baseUrl.substring(0, slash)) + "/" + TourApiLang.serviceId(lang);
    }

    private boolean isKorean(String lang) {
        return "KorService2".equals(TourApiLang.serviceId(lang));
    }

    private String nonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private JsonNode firstItem(JsonNode root) {
        JsonNode item = root == null ? null : root.at("/response/body/items/item");
        if (item == null || item.isMissingNode() || item.isNull()) return null;
        if (item.isArray()) return item.isEmpty() ? null : item.get(0);
        return item.isObject() ? item : null;
    }

    private void addFields(Map<String, String> target, JsonNode source, String... fields) {
        for (String field : fields) {
            String value = text(source, field);
            if (!value.isBlank()) target.put(field, value);
        }
    }

    private Map<String, String> localizeOperatingLabels(Map<String, String> details, String lang) {
        if (details == null || details.isEmpty()) return Map.of();
        Map<String, String> localized = new LinkedHashMap<>();
        details.forEach((key, value) -> localized.put(detailLabel(key, lang), value));
        return localized;
    }

    private String detailLabel(String key, String lang) {
        String normalized = overlayLanguage(lang);
        if ("ko".equals(normalized)) {
            return koreanDetailLabel(key);
        }
        String group = switch (key) {
            case "usetime", "usetimeculture", "usetimeleports", "opentime", "opentimefood", "playtime" -> "hours";
            case "restdate", "restdateculture", "restdateleports", "restdateshopping", "restdatefood" -> "closed";
            case "usefee", "usetimefestival", "usefeeleports" -> "fee";
            case "parking", "parkingculture", "parkinglodging" -> "parking";
            case "infocenter", "infocentertourcourse" -> "contact";
            case "reservation", "reservationfood", "reservationurl" -> "reservation";
            case "firstmenu" -> "menu";
            default -> key;
        };
        Map<String, Map<String, String>> labels = Map.of(
                "en", Map.of("hours", "Hours", "closed", "Closed", "fee", "Fee", "parking", "Parking", "contact", "Contact", "reservation", "Reservation", "menu", "Signature menu"),
                "ja", Map.of("hours", "利用時間", "closed", "休業日", "fee", "利用料金", "parking", "駐車", "contact", "お問い合わせ", "reservation", "予約案内", "menu", "代表メニュー"),
                "zh", Map.of("hours", "开放时间", "closed", "休息日", "fee", "使用费", "parking", "停车", "contact", "咨询", "reservation", "预约指南", "menu", "招牌菜"),
                "fr", Map.of("hours", "Horaires", "closed", "Fermeture", "fee", "Tarif", "parking", "Parking", "contact", "Contact", "reservation", "Réservation", "menu", "Menu signature"),
                "ko", Map.of("hours", "이용 시간", "closed", "휴무일", "fee", "이용 요금", "parking", "주차", "contact", "문의", "reservation", "예약 안내", "menu", "대표 메뉴"));
        return labels.getOrDefault(normalized, labels.get("ko")).getOrDefault(group, key);
    }

    private String koreanDetailLabel(String key) {
        return switch (key) {
            case "usetime", "usetimeculture", "usetimeleports", "opentime", "opentimefood", "playtime" -> "이용 시간";
            case "restdate", "restdateculture", "restdateleports", "restdateshopping", "restdatefood" -> "휴무일";
            case "usefee", "usetimefestival", "usefeeleports" -> "이용 요금";
            case "parking", "parkingculture", "parkinglodging" -> "주차";
            case "infocenter", "infocentertourcourse" -> "문의";
            case "reservation", "reservationfood", "reservationurl" -> "예약 안내";
            case "firstmenu" -> "대표 메뉴";
            case "treatmenu" -> "취급 메뉴";
            case "eventstartdate" -> "행사 시작일";
            case "eventenddate" -> "행사 종료일";
            case "eventplace" -> "행사 장소";
            case "spendtimefestival" -> "관람 소요 시간";
            case "distance" -> "코스 총거리";
            case "schedule" -> "코스 일정";
            case "taketime" -> "소요 시간";
            case "openperiod" -> "개장 기간";
            case "expagerangeleports" -> "체험 가능 연령";
            case "checkintime" -> "입실 시간";
            case "checkouttime" -> "퇴실 시간";
            case "roomcount" -> "객실 수";
            case "saleitem" -> "판매 품목";
            default -> key;
        };
    }

    private String overlayLanguage(String lang) {
        if (lang == null) return "ko";
        return lang.toLowerCase().startsWith("zh") ? "zh" : lang.toLowerCase();
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value.isBlank() ? text(node, second) : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }
}

