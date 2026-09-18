package com.example.demo.tourism.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DeepL Free 단문 번역. 관광 상세 개요처럼 국문만 오는 텍스트를 요청 언어로 옮긴다.
 *
 * <p>키({@code translation.api.key})가 없거나 실패·월 한도 초과면 {@code null}을 돌려주고,
 * 호출한 쪽은 국문 원문을 그대로 보여준다. 온디맨드로만 쓰이므로(사람이 연 상세) 캐시는 호출부 담당.
 */
@Component
public class DeeplTranslator {

    private static final Logger log = LoggerFactory.getLogger(DeeplTranslator.class);
    private static final Set<String> SUPPORTED = Set.of("EN", "JA", "ZH", "FR");

    private final RestClient client = RestClient.create();

    @Value("${translation.api.key:}")
    private String apiKey;

    /** langCode: ko/en/ja/zh/fr. ko이거나 미지원·키없음·실패면 null. */
    public String translate(String text, String langCode) {
        if (apiKey == null || apiKey.isBlank() || text == null || text.isBlank()) return null;
        String target = langCode == null ? "" : langCode.trim().toUpperCase();
        if (!SUPPORTED.contains(target)) return null;   // ko 포함

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("text", text);
        form.add("source_lang", "KO");
        form.add("target_lang", target);
        try {
            Map<?, ?> body = post(form);
            Object translations = body == null ? null : body.get("translations");
            if (translations instanceof List<?> list && !list.isEmpty()
                    && list.get(0) instanceof Map<?, ?> first) {
                Object t = first.get("text");
                return t == null ? null : t.toString();
            }
        } catch (Exception e) {
            log.warn("DeepL 번역 실패 (target {}): {}", target, e.getMessage());
        }
        return null;
    }

    /**
     * 연결이 끊겨 실패하면 한 번 더 보낸다.
     *
     * <p>번역기 서버가 오래 쓴 연결을 닫으면("GOAWAY received") 그 연결로 보낸 요청이 바로
     * 실패한다. 번역기 쪽 문제가 아니라 새 연결로 다시 보내면 된다. 소개글은 실패하면 국문을
     * 기억해 두므로, 이걸 그냥 두면 앱을 다시 켤 때까지 그 장소 소개가 국문으로 남았다.
     */
    private Map<?, ?> post(MultiValueMap<String, String> form) {
        try {
            return send(form);
        } catch (ResourceAccessException first) {
            log.info("DeepL 연결이 끊겨 한 번 더 보낸다: {}", first.getMessage());
            return send(form);
        }
    }

    private Map<?, ?> send(MultiValueMap<String, String> form) {
        return client.post()
                .uri("https://api-free.deepl.com/v2/translate")
                .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
    }

    /**
     * 여러 문장을 한 번에 보낸다. 순서는 보낸 대로 돌아온다. 하나라도 못 받으면 null.
     *
     * <p>글자로만 보낸다(태그 처리 없음). 줄바꿈은 그대로 살려 달라고 한다. 태그를 살려 보내면
     * 번역기가 {@code <br>} 을 "20:<br>00" 처럼 낱말 사이로 옮겨 놓았다.
     */
    public List<String> translateAll(List<String> texts, String langCode) {
        if (apiKey == null || apiKey.isBlank() || texts == null || texts.isEmpty()) return null;
        String target = langCode == null ? "" : langCode.trim().toUpperCase();
        if (!SUPPORTED.contains(target)) return null;

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        texts.forEach(t -> form.add("text", t));
        form.add("source_lang", "KO");
        form.add("target_lang", target);
        form.add("preserve_formatting", "1");
        try {
            Map<?, ?> body = post(form);
            if (body == null || !(body.get("translations") instanceof List<?> list) || list.size() != texts.size()) {
                return null;
            }
            List<String> out = new java.util.ArrayList<>(list.size());
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || m.get("text") == null) return null;
                out.add(m.get("text").toString());
            }
            return out;
        } catch (Exception e) {
            log.warn("DeepL 번역 실패 (target {}, {}건): {}", target, texts.size(), e.getMessage());
            return null;
        }
    }
}
