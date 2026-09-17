package com.example.demo.transport.service;

import com.example.demo.community.service.PlaceLabelTranslator;
import com.example.demo.tourism.config.DeeplTranslator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 교통 화면의 역·정류장·노선·장소 이름을 화면 언어로 옮긴다.
 *
 * <p>경로는 국문으로만 온다. 예전에는 상세 화면이 이름마다 번역기를 따로 불렀고, 결과를 기억하지
 * 않아 같은 경로를 열 때마다 글자 수를 썼다. 한 낱말만 보내다 보니 "서면" 이 일본어·중국어에서
 * 書面(서류)으로 나오기도 했다.
 *
 * <ol>
 *   <li>부산 도시철도 역 이름은 공식 표기 사전(i18n/busan-metro-stations.json)에서 찾는다.</li>
 *   <li>커뮤니티 장소 꼬리표 표(구 이름, 관광지)에 있으면 그 표기를 쓴다.</li>
 *   <li>나머지(정류장, 노선, 입력한 장소)는 번역기에 한 번에 보내고 기억한다. 실패는 기억하지 않는다.</li>
 * </ol>
 */
@Component
public class TransportNameTranslator {

    private static final Logger log = LoggerFactory.getLogger(TransportNameTranslator.class);
    private static final String RESOURCE = "i18n/busan-metro-stations.json";
    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Set<String> LANGS = Set.of("en", "ja", "zh", "fr");
    private static final int CACHE_LIMIT = 5_000;

    private final DeeplTranslator deepl;
    private final PlaceLabelTranslator places;
    private final ObjectMapper objectMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private Map<String, Map<String, String>> stations = Map.of();

    public TransportNameTranslator(DeeplTranslator deepl, PlaceLabelTranslator places, ObjectMapper objectMapper) {
        this.deepl = deepl;
        this.places = places;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 역 이름도 번역기로 옮긴다.", RESOURCE);
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
            stations = parsed;
        } catch (Exception e) {
            log.warn("{} 를 읽지 못했다 — 역 이름도 번역기로 옮긴다.", RESOURCE, e);
        }
    }

    /** "zh-CN" → "zh". 옮길 언어가 아니면(국문 포함) null. */
    static String language(String lang) {
        if (lang == null) return null;
        String l = lang.trim().toLowerCase();
        int dash = l.indexOf('-');
        if (dash > 0) l = l.substring(0, dash);
        return LANGS.contains(l) ? l : null;
    }

    /** 이름 하나. 못 옮기면 받은 그대로. */
    public String translate(String text, String lang) {
        return translateAll(List.of(text == null ? "" : text), lang).getOrDefault(text, text);
    }

    /**
     * 여러 이름을 한 번에. 돌려주는 표에는 옮긴 것만 들어 있다(국문 → 그 언어).
     * 국문 화면이거나 한글이 없는 이름은 넣지 않는다.
     */
    public Map<String, String> translateAll(Collection<String> texts, String lang) {
        String language = language(lang);
        Map<String, String> result = new LinkedHashMap<>();
        if (language == null || texts == null) return result;

        Set<String> pending = new LinkedHashSet<>();
        for (String text : texts) {
            if (text == null || result.containsKey(text) || !HANGUL.matcher(text).find()) continue;
            String known = known(text.trim(), language);
            if (known != null) result.put(text, known);
            else pending.add(text);
        }
        if (pending.isEmpty()) return result;

        List<String> sources = new ArrayList<>(pending.size());
        pending.forEach(t -> sources.add(t.trim()));
        List<String> translated = deepl.translateAll(sources, language);
        if (translated == null) return result;   // 국문 그대로. 기억하지 않는다.

        if (cache.size() > CACHE_LIMIT) cache.clear();
        int i = 0;
        for (String text : pending) {
            String t = translated.get(i++);
            if (t == null || t.isBlank()) continue;
            cache.put(language + '|' + text.trim(), t.trim());
            result.put(text, t.trim());
        }
        return result;
    }

    /** 사전·표·기억에 있는 이름. 없으면 null. */
    private String known(String name, String language) {
        String station = station(name, language);
        if (station != null) return station;

        String label = places.translate(language, name, null);
        if (label != null && !label.equals(name)) return label;

        return cache.get(language + '|' + name);
    }

    /** "서면", "서면역", "부산역" → 그 언어 역 이름. 역 이름이 아니면 null. */
    String station(String name, String language) {
        Map<String, String> byLang = stations.get(name);
        boolean withSuffix = false;
        if (byLang == null && name.endsWith("역") && name.length() > 1) {
            byLang = stations.get(name.substring(0, name.length() - 1));
            withSuffix = true;
        }
        if (byLang == null) return null;
        String v = byLang.get(language);
        if (v == null || v.isBlank()) return null;
        return withSuffix ? withStationWord(v, language) : v;
    }

    /** 이름 뒤 "역" 을 그 언어로. 이미 붙어 있으면 그대로. */
    private static String withStationWord(String name, String language) {
        return switch (language) {
            case "ja" -> name.endsWith("駅") ? name : name + "駅";
            case "zh" -> name.endsWith("站") ? name : name + "站";
            case "fr" -> name.startsWith("Gare") ? name : "Station " + name;
            default -> name.endsWith("Station") ? name : name + " Station";
        };
    }
}
