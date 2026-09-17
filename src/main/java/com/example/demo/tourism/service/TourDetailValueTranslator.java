package com.example.demo.tourism.service;

import com.example.demo.tourism.config.DeeplTranslator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 관광·맛집 상세의 운영정보 값(이용시간, 휴무일, 요금, 대표 메뉴 …)을 요청 언어로 옮긴다.
 *
 * <p>관광공사 상세는 국문 서비스에서만 받아서, 일본어로 봐도 "연중무휴", "매주 일요일" 이
 * 그대로 보였다. 같은 화면의 소개글은 이미 번역기로 옮기고 있어 같은 번역기를 쓴다.
 *
 * <ul>
 *   <li>짧고 자주 나오는 값은 이름표(i18n/tour-detail-values.json)로 옮긴다. 한 낱말만 보내면
 *       번역기가 앞뒤를 몰라 "가능" 을 Possible 로 옮겼다.</li>
 *   <li>나머지는 번역기로 한 번에 보낸다. 태그는 줄바꿈으로 바꿔 글자로만 보낸다.</li>
 *   <li>메뉴 칸은 번역 뒤에 국문을 괄호로 붙인다. 음식 이름은 번역기가 자주 틀리고
 *       ("회비빔밥" 이 会費ビビンバ), 식당에서 메뉴판과 맞춰 보기에도 국문이 낫다.</li>
 *   <li>한글이 없는 값(시각, 전화번호)은 그대로 둔다.</li>
 * </ul>
 *
 * <p>화면은 값을 HTML 로 받아 글자만 꺼내 쓴다. 그래서 옮긴 값은 HTML 이스케이프해서 돌려준다.
 * 번역에 실패하면 국문을 그대로 두고, 실패는 기억하지 않는다(다음에 다시 시도).
 */
@Component
public class TourDetailValueTranslator {

    private static final Logger log = LoggerFactory.getLogger(TourDetailValueTranslator.class);
    private static final String RESOURCE = "i18n/tour-detail-values.json";
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Pattern BR = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    /** 주소 바로 뒤에 한글이 붙어 온다("…busan.go.kr전화"). 번역기가 주소의 일부로 보고 남긴다. */
    private static final Pattern URL_THEN_HANGUL =
            Pattern.compile("(https?://[^\\s\\uAC00-\\uD7A3]+)([\\uAC00-\\uD7A3])");
    private static final Set<String> MENU_FIELDS = Set.of("firstmenu", "treatmenu");
    /** 같은 값이 장소마다 되풀이된다("연중무휴"). 너무 커지면 비운다. */
    private static final int CACHE_LIMIT = 5_000;

    private final DeeplTranslator deepl;
    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private Map<String, Map<String, String>> fixed = Map.of();

    public TourDetailValueTranslator(DeeplTranslator deepl, ObjectMapper objectMapper) {
        this.deepl = deepl;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 운영정보 값은 모두 번역기로 옮긴다.", RESOURCE);
                return;
            }
            Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() { });
            Map<String, Map<String, String>> parsed = new HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey().startsWith("_") || !(e.getValue() instanceof Map<?, ?> m)) continue;
                Map<String, String> byLang = new HashMap<>();
                m.forEach((k, v) -> byLang.put(String.valueOf(k), String.valueOf(v)));
                parsed.put(normalize(e.getKey()), byLang);
            }
            fixed = parsed;
        } catch (Exception e) {
            log.warn("{} 를 읽지 못했다 — 운영정보 값은 모두 번역기로 옮긴다.", RESOURCE, e);
        }
    }

    /**
     * 운영정보 값들을 이 언어로 옮긴 새 표. 국문이거나 옮길 값이 없으면 받은 표를 그대로 돌려준다.
     *
     * @param details 필드 이름 → 국문 값(HTML 이 섞여 있을 수 있음)
     * @param lang    en, ja, zh, fr
     */
    public Map<String, String> translate(Map<String, String> details, String lang) {
        if (details == null || details.isEmpty() || lang == null || "ko".equalsIgnoreCase(lang)) return details;
        String language = lang.toLowerCase();

        Map<String, String> result = new LinkedHashMap<>(details);
        List<String> pendingKeys = new ArrayList<>();
        List<String> pendingPlain = new ArrayList<>();

        for (Map.Entry<String, String> e : details.entrySet()) {
            String value = e.getValue();
            if (value == null || !HANGUL.matcher(value).find()) continue;
            String plain = plain(value);

            String known = lookup(plain, language);
            if (known != null) {
                result.put(e.getKey(), render(e.getKey(), known, plain));
                continue;
            }
            pendingKeys.add(e.getKey());
            pendingPlain.add(plain);
        }
        if (pendingPlain.isEmpty()) return result;

        List<String> translated = deepl.translateAll(pendingPlain, language);
        if (translated == null) return result;   // 국문 그대로. 기억하지 않는다.

        if (cache.size() > CACHE_LIMIT) cache.clear();
        for (int i = 0; i < pendingKeys.size(); i++) {
            String t = translated.get(i);
            if (t == null || t.isBlank()) continue;
            cache.put(cacheKey(language, pendingPlain.get(i)), t);
            result.put(pendingKeys.get(i), render(pendingKeys.get(i), t, pendingPlain.get(i)));
        }
        return result;
    }

    private String lookup(String plain, String language) {
        Map<String, String> byLang = fixed.get(normalize(plain));
        if (byLang != null && byLang.get(language) != null) return byLang.get(language);
        return cache.get(cacheKey(language, plain));
    }

    /** 화면에 넘길 값. 메뉴 칸은 국문을 괄호로 덧붙인다. */
    private static String render(String field, String translated, String koreanPlain) {
        String text = translated.trim();
        if (MENU_FIELDS.contains(field) && !text.equals(koreanPlain.trim())) {
            text = text + " (" + koreanPlain.trim() + ")";
        }
        return HtmlUtils.htmlEscape(text, "UTF-8");   // < > & " ' 만. 악센트 글자는 그대로
    }

    /** 태그를 걷어 글자로. 줄바꿈 태그는 줄바꿈으로 남긴다. */
    static String plain(String html) {
        String withBreaks = BR.matcher(html).replaceAll("\n");
        String text = HtmlUtils.htmlUnescape(TAG.matcher(withBreaks).replaceAll("")).trim();
        return URL_THEN_HANGUL.matcher(text).replaceAll("$1 $2");
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ");
    }

    private static String cacheKey(String language, String plain) {
        return language + '|' + plain;
    }
}
