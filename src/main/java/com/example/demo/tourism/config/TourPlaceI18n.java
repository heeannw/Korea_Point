package com.example.demo.tourism.config;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourPlaceTranslationFiller;
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
 * 표에 없는 contentId 는 {@link TourPlaceTranslationFiller} 가 뒤에서 채운다.
 * 채워지기 전까지는 국문 원문을 그대로 둔다.</p>
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
    private final TourPlaceTranslationFiller filler;
    private Map<String, Map<String, Entry>> byLang = Map.of();

    public TourPlaceI18n(ObjectMapper objectMapper, TourPlaceTranslationFiller filler) {
        this.objectMapper = objectMapper;
        this.filler = filler;
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
