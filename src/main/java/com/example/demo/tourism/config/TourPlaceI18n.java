package com.example.demo.tourism.config;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관광 목록·상세는 항상 KorService2(국문)에서 받고, 국문 외 언어는 여기서 이름·주소만 바꿔 끼운다.
 *
 * <p>표는 빌드 타임 스크립트({@code scripts/tourism/fetch_tour_i18n.py})가 만든
 * {@code tourdata/tourism/tour_i18n_<lang>.json} 이다.
 * TourAPI 언어별 서비스는 국문과 <b>contentId 체계가 완전히 달라</b>(교집합 0) 조인이 불가능해서,
 * 값은 전부 DeepL Free 기계번역이다(이름·주소만). 개요·운영정보는 v1에서 국문을 유지한다.
 * 표에 없는 contentId 는 국문 원문을 그대로 둔다.</p>
 */
@Component
public class TourPlaceI18n {

    private static final Logger log = LoggerFactory.getLogger(TourPlaceI18n.class);
    private static final String DIR = "tourdata/tourism/";
    /** ko 는 기준 언어라 오버레이가 없다. */
    private static final Map<String, String> RESOURCE_BY_LANG = Map.of(
            "en", DIR + "tour_i18n_en.json",
            "ja", DIR + "tour_i18n_ja.json",
            "zh", DIR + "tour_i18n_zh.json",
            "fr", DIR + "tour_i18n_fr.json");

    /** contentId -> {"title": "...", "addr": "..."} */
    private record Entry(String title, String addr) { }

    private final ObjectMapper objectMapper;
    private Map<String, Map<String, Entry>> byLang = Map.of();

    public TourPlaceI18n(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        Map<String, Map<String, Entry>> loaded = new LinkedHashMap<>();
        RESOURCE_BY_LANG.forEach((lang, resource) -> {
            Map<String, Entry> table = read(resource);
            if (!table.isEmpty()) {
                loaded.put(lang, table);
                log.info("관광 번역 오버레이 로드: {} ({}건)", lang, table.size());
            }
        });
        byLang = loaded;
    }

    private Map<String, Entry> read(String resource) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("{} 없음 — 해당 언어 목록은 이름·주소가 국문으로 표시된다. "
                        + "scripts/fetch_tour_i18n.py 를 실행해 생성하라.", resource);
                return Map.of();
            }
            return objectMapper.readValue(in, new TypeReference<Map<String, Entry>>() { });
        } catch (Exception e) {
            log.warn("{} 을 읽지 못했다 — 국문으로 표시된다.", resource, e);
            return Map.of();
        }
    }

    private Entry lookup(String lang, String contentId) {
        if (lang == null || contentId == null) return null;
        Map<String, Entry> table = byLang.get(lang.toLowerCase());
        return table == null ? null : table.get(contentId);
    }

    /** 국문으로 채워진 DTO의 이름·주소를 요청 언어로 바꿔 끼운다. 번역이 없으면 그대로 둔다. */
    public void apply(String lang, TourPlaceDto place) {
        if (place == null) return;
        Entry e = lookup(lang, place.getId());
        if (e == null) return;
        if (e.title() != null && !e.title().isBlank()) place.setTitle(e.title());
        if (e.addr() != null && !e.addr().isBlank()) place.setAddr(e.addr());
    }

    /** 상세 패널 제목처럼 문자열 하나만 필요한 경우. 번역이 없으면 국문을 돌려준다. */
    public String translateTitle(String lang, String contentId, String koreanTitle) {
        Entry e = lookup(lang, contentId);
        return (e != null && e.title() != null && !e.title().isBlank()) ? e.title() : koreanTitle;
    }

    /** 주소만 필요한 경우(홈 카드 등). 번역이 없으면 국문을 돌려준다. */
    public String translateAddress(String lang, String contentId, String koreanAddress) {
        Entry e = lookup(lang, contentId);
        return (e != null && e.addr() != null && !e.addr().isBlank()) ? e.addr() : koreanAddress;
    }
}
