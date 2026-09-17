package com.example.demo.community.service;

import com.example.demo.tourism.config.TourPlaceI18n;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 커뮤니티 글에 붙은 장소 꼬리표를 화면 언어로 바꾼다.
 *
 * <p>꼬리표는 글을 쓸 때의 국문 이름 그대로 저장돼 있어서, 일본어로 봐도 "강서구",
 * "광안리해수욕장" 이 그대로 보였다.
 *
 * <p>순서가 중요하다. 홍보 글은 꼬리표 자리에 구·군 이름을 넣고 장소 번호에는 가게 번호를
 * 넣어 둔다. 번호부터 보면 구 이름 대신 가게 이름이 나온다. 그래서 이름표를 먼저 보고,
 * 없을 때만 번호로 찾는다.
 */
@Component
public class PlaceLabelTranslator {

    private static final Logger log = LoggerFactory.getLogger(PlaceLabelTranslator.class);
    private static final String RESOURCE = "i18n/place-labels.json";

    private final ObjectMapper objectMapper;
    private final TourPlaceI18n placeI18n;

    /** 국문 꼬리표 -> 언어별 표기. */
    private Map<String, Map<String, String>> labels = Map.of();

    public PlaceLabelTranslator(ObjectMapper objectMapper, TourPlaceI18n placeI18n) {
        this.objectMapper = objectMapper;
        this.placeI18n = placeI18n;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 장소 꼬리표를 국문으로 보여 준다.", RESOURCE);
                return;
            }
            Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() { });
            Map<String, Map<String, String>> parsed = new HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey().startsWith("_") || !(e.getValue() instanceof Map<?, ?> m)) continue;
                Map<String, String> byLang = new HashMap<>();
                m.forEach((k, v) -> byLang.put(String.valueOf(k), String.valueOf(v)));
                parsed.put(e.getKey(), byLang);
            }
            labels = parsed;
        } catch (Exception e) {
            log.warn("{} 를 읽지 못했다 — 장소 꼬리표를 국문으로 보여 준다.", RESOURCE, e);
        }
    }

    /** 표에 든 이름 수. 확인용. */
    int size() {
        return labels.size();
    }

    /**
     * 꼬리표를 이 언어로. 모르면 받은 그대로 돌려준다.
     *
     * @param label     저장된 국문 꼬리표
     * @param contentId 글에 붙은 장소 번호. 없을 수 있다.
     */
    public String translate(String lang, String label, String contentId) {
        if (label == null || lang == null || "ko".equals(lang)) return label;

        Map<String, String> byLang = labels.get(label.trim());
        if (byLang != null) {
            String v = byLang.get(lang);
            if (v != null && !v.isBlank()) return v;
        }
        // 번호로 찾되, 못 찾았다고 번역을 맡기지는 않는다. 넘길 국문 이름이 그 장소 이름이라는
        // 보장이 없어서(홍보 글의 구 이름처럼) 잘못된 이름이 그 장소의 번역으로 굳을 수 있다.
        String found = placeI18n.findTitle(lang, contentId);
        return found != null ? found : label;
    }
}
