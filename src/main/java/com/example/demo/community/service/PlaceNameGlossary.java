package com.example.demo.community.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 부산 지명을 번역기가 늘 같은 이름으로 옮기게 한다.
 *
 * <p>그냥 맡기면 번역기가 이름을 매번 새로 지어낸다. 실제로 이런 것들이 나왔다.
 * <ul>
 *   <li>기장이 중국어에서 机张, 基长, 基长县 세 가지</li>
 *   <li>흰여울이 일본어에서 뜻을 풀어버린 「白い波の文化村」과 ヒンヨウル 두 가지</li>
 *   <li>흰여울이 불어에서 Hwanyeoul, Hwinyu, Hwineul 세 가지 철자</li>
 *   <li>영도가 중국어에서 永道(틀림)와 影岛 섞임</li>
 * </ul>
 * 같은 사이트 안에서 같은 동네를 다르게 부르면 읽는 사람이 다른 곳으로 안다.
 *
 * <p>번역기의 용어집에 이름을 등록해 둔다. 태그로 감싸 "손대지 말라"고 하는 방법도 있는데,
 * 그렇게 하면 감싼 조각이 문장 밖으로 밀려나 어순이 망가진다(실제로 해 보고 접었다).
 *
 * <p>무료 계정은 용어집을 한 번에 하나만 둘 수 있다. 그래서 언어를 바꿀 때마다 기존 것을
 * 지우고 새로 만든다. 만드는 일이 잦지 않도록, 부르는 쪽이 한 차례에 한 언어만 다룬다.
 */
@Component
public class PlaceNameGlossary {

    private static final Logger log = LoggerFactory.getLogger(PlaceNameGlossary.class);
    private static final String RESOURCE = "i18n/place-names.json";
    private static final String BASE = "https://api-free.deepl.com/v2";

    /** 우리가 만든 용어집만 지우기 위한 이름표. */
    private static final String NAME_PREFIX = "koreapoint-places-";

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final ObjectMapper objectMapper;
    private final RestClient client;

    @Value("${translation.api.key:}")
    private String apiKey;

    /** 국문 이름 -> 언어별 표기. */
    private Map<String, Map<String, String>> names = Map.of();

    /** 지금 번역기에 올라가 있는 용어집. 언어가 바뀌면 갈아 끼운다. */
    private String currentLang;
    private String currentId;

    public PlaceNameGlossary(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 지명을 번역기에 그대로 맡긴다.", RESOURCE);
                return;
            }
            Map<String, Object> raw = objectMapper.readValue(in, new TypeReference<>() { });
            Map<String, Map<String, String>> parsed = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey().startsWith("_") || !(e.getValue() instanceof Map<?, ?> m)) continue;
                Map<String, String> byLang = new LinkedHashMap<>();
                m.forEach((k, v) -> byLang.put(String.valueOf(k), String.valueOf(v)));
                parsed.put(e.getKey(), byLang);
            }
            names = parsed;
            log.info("지명 표기 {}개 로드", names.size());
        } catch (Exception e) {
            log.warn("{} 를 읽지 못했다 — 지명을 번역기에 그대로 맡긴다.", RESOURCE, e);
        }
    }

    /** 표에 든 이름 수. 확인용. */
    public int size() {
        return names.size();
    }

    /**
     * 이 글에 표에 든 지명이 들어 있나.
     *
     * <p>들어 있는데 용어집을 못 얻었으면 그 batch 는 건너뛴다. 용어집 없이 옮기면
     * 지명이 제멋대로 나오는데(이기대가 伊基代, 다대포가 大代浦), 한 번 저장되면
     * 누가 다시 들여다보기 전까지 그대로 남는다. 늦게 채우는 편이 낫다.
     */
    public boolean mentions(String text) {
        if (text == null || text.isEmpty() || names.isEmpty()) return false;
        for (String ko : names.keySet()) {
            if (text.contains(ko)) return true;
        }
        return false;
    }

    /** 이 언어의 용어집 내용. 한 줄에 "국문\t그 언어 표기". */
    String entriesFor(String lang) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> e : names.entrySet()) {
            String v = e.getValue().get(lang);
            if (v == null || v.isBlank()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append('\t').append(v);
        }
        return sb.toString();
    }

    /**
     * 이 언어로 번역할 때 쓸 용어집 번호. 없거나 못 만들면 null 이고,
     * 그때는 지명을 번역기 마음대로 옮기게 둔다. 번역 자체는 그대로 된다.
     */
    public synchronized String idFor(String lang) {
        if (apiKey == null || apiKey.isBlank() || names.isEmpty()) return null;
        if (lang.equals(currentLang) && currentId != null) return currentId;

        String entries = entriesFor(lang);
        if (entries.isEmpty()) return null;
        try {
            deleteOurs();              // 무료 계정은 한 번에 하나뿐이다
            currentId = create(lang, entries);
            currentLang = lang;
            log.info("지명 용어집 교체: {} ({}개)", lang, names.size());
            return currentId;
        } catch (Exception e) {
            log.warn("지명 용어집을 만들지 못했다 ({}): {}", lang, e.getMessage());
            currentLang = null;
            currentId = null;
            return null;
        }
    }

    private String create(String lang, String entries) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("name", NAME_PREFIX + lang);
        form.add("source_lang", "ko");
        form.add("target_lang", lang);
        form.add("entries", entries);
        form.add("entries_format", "tsv");
        Map<?, ?> body = client.post().uri(BASE + "/glossaries")
                .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
        Object id = body == null ? null : body.get("glossary_id");
        if (id == null) throw new IllegalStateException("용어집 번호를 못 받았다");
        return id.toString();
    }

    /** 우리가 만든 것만 지운다. 남의 용어집은 건드리지 않는다. */
    private void deleteOurs() {
        Map<?, ?> body = client.get().uri(BASE + "/glossaries")
                .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                .retrieve().body(Map.class);
        if (body == null || !(body.get("glossaries") instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> g)) continue;
            Object name = g.get("name");
            Object id = g.get("glossary_id");
            if (name == null || id == null || !name.toString().startsWith(NAME_PREFIX)) continue;
            try {
                client.delete().uri(BASE + "/glossaries/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                        .retrieve().toBodilessEntity();
            } catch (Exception e) {
                log.warn("옛 용어집을 못 지웠다 ({}): {}", name, e.getMessage());
            }
        }
    }
}
