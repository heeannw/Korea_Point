package com.example.demo.tourism.config;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourPlaceTranslationFiller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관광 목록·상세는 항상 KorService2(국문)에서 받고, 국문 외 언어는 여기서 이름·주소만 바꿔 끼운다.
 *
 * <p>언어별 공식 서비스(Eng/Jpn/Chs/Fre)를 서버가 직접, 주기적으로 불러 기준표를 만든다.
 * 다만 이 서비스들은 국문과 contentId 체계가 완전히 달라(부산 기준 교집합 0건, 실측 확인) 이
 * 표에서 실제로 매칭되는 항목은 거의 없다 — 그래도 정적 파일 대신 관광공사 API를 실시간으로
 * 쓴다는 원칙은 지킨다. 표에 없는 항목은 {@link TourPlaceTranslationFiller}가 DeepL로 채운다
 * (이미 실시간이고 DB에 영속돼 있다). 예전엔 이 표를 사람이 빌드 스크립트로 미리 받아 파일로
 * 커밋해 뒀다({@code scripts/tourism/fetch_tour_i18n.py}, 삭제됨).</p>
 */
@Component
public class TourPlaceI18n {

    private static final Logger log = LoggerFactory.getLogger(TourPlaceI18n.class);
    private static final long REFRESH_MS = 24 * 60 * 60_000L; // 하루
    /** ko 는 기준 언어라 오버레이가 없다. */
    private static final List<String> LANGS = List.of("en", "ja", "zh", "fr");
    private static final int MAX_PAGES = 10;

    /** contentId -> {"title": "...", "addr": "..."} */
    private record Entry(String title, String addr) { }

    private final TourApiProperties properties;
    private final ObjectMapper objectMapper;
    private final TourPlaceTranslationFiller filler;
    private final RestClient restClient = RestClient.create();
    private volatile Map<String, Map<String, Entry>> byLang = Map.of();

    public TourPlaceI18n(TourApiProperties properties, ObjectMapper objectMapper, TourPlaceTranslationFiller filler) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.filler = filler;
    }

    @PostConstruct
    @Scheduled(fixedRate = REFRESH_MS)
    void refresh() {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) return;
        Map<String, Map<String, Entry>> loaded = new LinkedHashMap<>();
        for (String lang : LANGS) {
            try {
                Map<String, Entry> table = fetchAreaList(properties.baseUrlFor(lang));
                if (!table.isEmpty()) {
                    loaded.put(lang, table);
                    log.info("관광 번역 오버레이 로드: {} ({}건)", lang, table.size());
                }
            } catch (Exception e) {
                log.warn("관광 번역 오버레이 갱신 실패 [{}]: {}", lang, e.getMessage());
            }
        }
        // 전부 실패해도 이전에 성공한 표를 그대로 둔다(비었으면 필러가 국문 채운 채로 물려받는다).
        if (!loaded.isEmpty()) byLang = loaded;
    }

    /** 부산 areaBasedList2 전체를 언어별 서비스에서 받는다. contentId -> {title, addr}. */
    private Map<String, Entry> fetchAreaList(String baseUrl) {
        Map<String, Entry> out = new LinkedHashMap<>();
        int pageSize = Math.max(1, properties.getDefaultRows());
        for (int page = 1; page <= MAX_PAGES; page++) {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/areaBasedList2")
                    .queryParam("serviceKey", properties.getServiceKey())
                    .queryParam("MobileOS", properties.getMobileOs())
                    .queryParam("MobileApp", properties.getMobileApp())
                    .queryParam("_type", "json")
                    .queryParam("numOfRows", pageSize)
                    .queryParam("pageNo", page)
                    .queryParam("lDongRegnCd", properties.getBusanLegalDongCode())
                    .queryParam("arrange", "Q")
                    .build().encode().toUri();
            JsonNode body = requestBody(uri);
            addItems(out, body.path("items").path("item"));
            if ((long) page * pageSize >= body.path("totalCount").asLong(0)) break;
        }
        return out;
    }

    private JsonNode requestBody(URI uri) {
        String raw = restClient.get().uri(uri).retrieve().body(String.class);
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new RuntimeException("번역 오버레이 응답 파싱 실패", e);
        }
        String resultCode = root.path("response").path("header").path("resultCode").asText();
        if (!"0000".equals(resultCode)) {
            throw new RuntimeException("TourAPI 오류 " + resultCode + ": "
                    + root.path("response").path("header").path("resultMsg").asText());
        }
        return root.path("response").path("body");
    }

    private void addItems(Map<String, Entry> out, JsonNode items) {
        if (items.isArray()) {
            for (JsonNode item : items) addItem(out, item);
        } else if (items.isObject()) {
            addItem(out, items);
        }
    }

    private void addItem(Map<String, Entry> out, JsonNode item) {
        String cid = text(item, "contentid");
        if (cid == null) return;
        String addr1 = text(item, "addr1");
        String addr2 = text(item, "addr2");
        String addr = addr1 == null ? addr2 : (addr2 == null ? addr1 : addr1 + " " + addr2);
        out.putIfAbsent(cid, new Entry(text(item, "title"), addr));
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText("").trim();
        return v.isEmpty() ? null : v;
    }

    private Entry lookup(String lang, String contentId) {
        if (lang == null || contentId == null) return null;
        Map<String, Entry> table = byLang.get(lang.toLowerCase());
        return table == null ? null : table.get(contentId);
    }

    /**
     * 표에서 찾고, 없으면 나중에 채운 것에서 찾는다. 둘 다 없으면 보충 대상으로 적어 두고
     * null 을 돌려준다. 적어 두기만 할 뿐 여기서 번역기를 부르지는 않는다.
     */
    private Entry resolve(String lang, String contentId, String koTitle, String koAddress) {
        Entry e = lookup(lang, contentId);
        if (e != null) return e;

        TourPlaceTranslationFiller.Text filled = filler.find(lang, contentId);
        if (filled != null) return new Entry(filled.title(), filled.address());

        filler.remember(lang, contentId, koTitle, koAddress);
        return null;
    }

    /** 국문으로 채워진 DTO의 이름·주소를 요청 언어로 바꿔 끼운다. 번역이 없으면 그대로 둔다. */
    public void apply(String lang, TourPlaceDto place) {
        if (place == null) return;
        Entry e = resolve(lang, place.getId(), place.getTitle(), place.getAddr());
        if (e == null) return;
        if (e.title() != null && !e.title().isBlank()) place.setTitle(e.title());
        if (e.addr() != null && !e.addr().isBlank()) place.setAddr(e.addr());
    }

    /** 상세 패널 제목처럼 문자열 하나만 필요한 경우. 번역이 없으면 국문을 돌려준다. */
    public String translateTitle(String lang, String contentId, String koreanTitle) {
        Entry e = resolve(lang, contentId, koreanTitle, null);
        return (e != null && e.title() != null && !e.title().isBlank()) ? e.title() : koreanTitle;
    }

    /**
     * 번역된 이름을 찾기만 한다. 없으면 null 이고, 보충 대상으로 적어 두지도 않는다.
     *
     * <p>넘겨받은 국문 이름이 그 장소의 이름인지 확신할 수 없을 때 쓴다. 커뮤니티 홍보 글은
     * 가게 번호에 구 이름을 붙여 두는데, 그걸 {@link #translateTitle} 로 넘기면 구 이름이
     * 그 가게의 번역으로 저장된다.
     */
    public String findTitle(String lang, String contentId) {
        Entry e = lookup(lang, contentId);
        if (e != null && e.title() != null && !e.title().isBlank()) return e.title();
        if (lang == null || contentId == null) return null;
        TourPlaceTranslationFiller.Text filled = filler.find(lang, contentId);
        return (filled != null && filled.title() != null && !filled.title().isBlank()) ? filled.title() : null;
    }

    /** 주소만 필요한 경우(홈 카드 등). 번역이 없으면 국문을 돌려준다. */
    public String translateAddress(String lang, String contentId, String koreanAddress) {
        Entry e = resolve(lang, contentId, null, koreanAddress);
        return (e != null && e.addr() != null && !e.addr().isBlank()) ? e.addr() : koreanAddress;
    }
}
