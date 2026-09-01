package com.example.demo.tourism.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
            Map<?, ?> body = client.post()
                    .uri("https://api-free.deepl.com/v2/translate")
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
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
}
