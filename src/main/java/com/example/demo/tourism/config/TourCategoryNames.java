package com.example.demo.tourism.config;

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
 * TourAPI 분류체계(lclsSystm) 코드를 사람이 읽는 이름으로 옮긴다.
 *
 * 기존 cat1/2/3은 부산 기준 17%만 채워져 있어 쓸 수 없고,
 * lclsSystm1/2/3은 100% 채워져 있어 이쪽을 쓴다.
 * 표는 언어별 서비스(Kor/Eng/Jpn/Chs/Fre)의 lclsSystmCode2 조회 결과를 리소스로 두었다.
 * 국문은 {@code lcls_systm.json}, 그 외는 {@code lcls_systm_<lang>.json}.
 * 지원하지 않는 언어나 코드는 국문 표로 되돌린다.
 */
@Component
public class TourCategoryNames {

    private static final Logger log = LoggerFactory.getLogger(TourCategoryNames.class);
    private static final String DIR = "tourdata/tourism/";
    private static final String DEFAULT_LANG = "ko";
    /** 국문(ko)은 접미사 없는 파일, 나머지는 lcls_systm_<lang>.json 을 읽는다. */
    private static final Map<String, String> RESOURCE_BY_LANG = Map.of(
            "ko", DIR + "lcls_systm.json",
            "en", DIR + "lcls_systm_en.json",
            "ja", DIR + "lcls_systm_ja.json",
            "zh", DIR + "lcls_systm_zh.json",
            "fr", DIR + "lcls_systm_fr.json");

    private final ObjectMapper objectMapper;
    /** lang -> (code -> name). 국문은 항상 존재하고 나머지는 로딩 성공 시에만 채워진다. */
    private Map<String, Map<String, String>> namesByLang = Map.of();

    public TourCategoryNames(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        Map<String, Map<String, String>> loaded = new LinkedHashMap<>();
        RESOURCE_BY_LANG.forEach((lang, resource) -> {
            Map<String, String> table = read(resource);
            if (!table.isEmpty()) loaded.put(lang, table);
        });
        if (!loaded.containsKey(DEFAULT_LANG)) {
            log.warn("{} 없음 — 관광 타입명 대신 콘텐츠 유형명으로 표시된다.",
                    RESOURCE_BY_LANG.get(DEFAULT_LANG));
        }
        namesByLang = loaded;
    }

    private Map<String, String> read(String resource) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("{} 없음 — 해당 언어 분류명은 국문으로 되돌린다.", resource);
                return Map.of();
            }
            return objectMapper.readValue(in, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            log.warn("{} 을 읽지 못했다 — 국문으로 되돌린다.", resource, e);
            return Map.of();
        }
    }

    /**
     * 소분류 이름을 우선 쓰고, 없으면 중분류·대분류로 물러난다.
     * 요청 언어 표에서 못 찾으면 국문 표로 한 번 더 물러나고,
     * 그래도 없으면 null을 돌려 호출한 쪽이 기존 유형명을 쓰게 한다.
     */
    /** 언어를 지정하지 않으면 국문 표를 쓴다. (관광 외 모듈의 기존 호출부 호환용) */
    public String resolve(String small, String medium, String large) {
        return resolve(DEFAULT_LANG, small, medium, large);
    }

    public String resolve(String lang, String small, String medium, String large) {
        Map<String, String> table = namesByLang.getOrDefault(
                normalize(lang), namesByLang.get(DEFAULT_LANG));
        Map<String, String> fallback = namesByLang.get(DEFAULT_LANG);

        String name = lookup(table, small);
        if (name == null) name = lookup(table, medium);
        if (name == null) name = lookup(table, large);
        if (name == null && table != fallback) {
            name = lookup(fallback, small);
            if (name == null) name = lookup(fallback, medium);
            if (name == null) name = lookup(fallback, large);
        }
        return name;
    }

    private String normalize(String lang) {
        if (lang == null) return DEFAULT_LANG;
        String lower = lang.toLowerCase();
        return RESOURCE_BY_LANG.containsKey(lower) ? lower : DEFAULT_LANG;
    }

    private String lookup(Map<String, String> table, String code) {
        if (table == null || code == null || code.isBlank()) return null;
        String name = table.get(code.trim());
        return name == null || name.isBlank() ? null : name;
    }

    public int size() {
        Map<String, String> table = namesByLang.get(DEFAULT_LANG);
        return table == null ? 0 : table.size();
    }
}
