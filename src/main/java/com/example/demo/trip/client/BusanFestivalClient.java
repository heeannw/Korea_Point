package com.example.demo.trip.client;

import com.example.demo.trip.integration.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * trip 전용 축제 조회 클라이언트. tourism 모듈(TourApiClient)을 건드리지 않고,
 * 같은 공공 TourAPI(searchFestival2)를 trip이 독립적으로 직접 호출한다.
 * tourism 쪽엔 축제 시작일/종료일이 밖으로 안 나와서, 사용자의 실제 여행 기간과
 * 겹치는 축제만 걸러내려면 이 필드가 꼭 필요해 별도로 만들었다.
 * 서비스키(tour-api.service-key)는 tourism과 같은 값을 그대로 재사용한다 — 설정만 공유, 코드는 독립.
 */
@Component
public class BusanFestivalClient {

    private static final Logger log = LoggerFactory.getLogger(BusanFestivalClient.class);
    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2/searchFestival2";
    /** 법정동 시도코드 — 부산. */
    private static final String BUSAN_LEGAL_DONG_CODE = "26";
    private static final int MAX_ROWS = 300;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 6000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tour-api.service-key:}")
    private String serviceKey;

    public BusanFestivalClient(ObjectMapper objectMapper) {
        this.restTemplate = ExternalApiClient.create(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        this.objectMapper = objectMapper;
    }

    /**
     * tripStart~tripEnd와 기간이 겹치는 부산 축제만 돌려준다.
     * 실패하거나 키가 없으면 예외 없이 빈 목록을 돌려줘서 일정 생성 자체는 항상 되게 한다.
     */
    public List<BusanFestivalDto> findOverlapping(LocalDate tripStart, LocalDate tripEnd) {
        if (serviceKey == null || serviceKey.isBlank()) return List.of();
        if (tripStart == null || tripEnd == null) return List.of();
        try {
            // eventStartDate는 "이 날짜 이후에 끝나는" 축제를 돌려준다. 오늘이 여행 시작일보다
            // 이르면 오늘부터 조회해야 이미 진행 중인 축제도 놓치지 않는다.
            LocalDate today = LocalDate.now();
            LocalDate queryFrom = today.isBefore(tripStart) ? today : tripStart;

            // serviceKey를 직접 percent-encode한다. UriComponentsBuilder.encode()는 '+'를
            // 쿼리에서 이미 합법 문자로 보고 그대로 두는데, data.go.kr은 그걸 공백으로 해석해
            // 인증키에 '+'가 섞여 있으면 키가 깨진다.
            String encodedKey = URLEncoder.encode(serviceKey, StandardCharsets.UTF_8);
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(BASE_URL)
                    .queryParam("serviceKey", encodedKey)
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "KoreaPoint")
                    .queryParam("_type", "json")
                    .queryParam("eventStartDate", queryFrom.format(DateTimeFormatter.BASIC_ISO_DATE))
                    .queryParam("arrange", "A")
                    .queryParam("numOfRows", MAX_ROWS)
                    .queryParam("pageNo", 1);
            URI uri = uriBuilder.build(true).toUri();

            String body = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(body);
            String resultCode = root.path("response").path("header").path("resultCode").asText();
            if (!"0000".equals(resultCode)) {
                log.warn("부산 축제 조회 실패({}): {}", resultCode,
                        root.path("response").path("header").path("resultMsg").asText());
                return List.of();
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");
            List<BusanFestivalDto> result = new ArrayList<>();
            if (items.isArray()) {
                for (JsonNode item : items) addIfOverlapping(result, item, tripStart, tripEnd);
            } else if (items.isObject()) {
                addIfOverlapping(result, items, tripStart, tripEnd);
            }
            return List.copyOf(result);
        } catch (Exception e) {
            log.warn("부산 축제 API 호출에 실패했습니다: {}", e.getMessage());
            return List.of();
        }
    }

    private void addIfOverlapping(List<BusanFestivalDto> out, JsonNode item, LocalDate tripStart, LocalDate tripEnd) {
        if (!BUSAN_LEGAL_DONG_CODE.equals(text(item, "lDongRegnCd"))) return;

        LocalDate start = parseDate(text(item, "eventstartdate"));
        LocalDate end = parseDate(text(item, "eventenddate"));
        if (start == null || end == null) return;
        if (end.isBefore(tripStart) || start.isAfter(tripEnd)) return; // 여행 기간과 안 겹침

        double lat = parseDouble(text(item, "mapy"));
        double lng = parseDouble(text(item, "mapx"));
        if (lat == 0 || lng == 0) return;

        String title = text(item, "title");
        if (title == null || title.isBlank()) return;

        out.add(new BusanFestivalDto(title, start, end, lat, lng,
                text(item, "addr1") == null ? "" : text(item, "addr1"),
                text(item, "firstimage") == null ? "" : text(item, "firstimage")));
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) return null;
        try { return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE); }
        catch (Exception e) { return null; }
    }

    private double parseDouble(String raw) {
        if (raw == null) return 0;
        try { return Double.parseDouble(raw); } catch (Exception e) { return 0; }
    }
}
