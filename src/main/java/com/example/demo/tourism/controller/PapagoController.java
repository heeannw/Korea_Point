package com.example.demo.tourism.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 파파고 번역 프록시 컨트롤러
 * 브라우저에서 파파고 API 직접 호출 시 CORS 에러 발생 → 서버에서 대신 호출
 *
 * POST /api/translate
 * Body: { "text": "번역할 텍스트", "source": "ko", "target": "en" }
 * Response: { "translatedText": "translated text" }
 */
@RestController
public class PapagoController {

    private static final String CLIENT_ID     = "tck710640b";
    private static final String CLIENT_SECRET = "GOws3Sv8ejwp0LDGABAn0DxhlHWWCQnKlBmAvNRl";
    //private static final String PAPAGO_URL    = "https://naveropenapi.apigw.ntruss.com/nmt/v1/translation";  // 구버전
    private static final String PAPAGO_URL = "https://papago.apigw.ntruss.com/nmt/v1/translation";  // 신버전

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/api/translate")
    public ResponseEntity<?> translate(@RequestBody Map<String, String> body) {
        String text   = body.getOrDefault("text",   "");
        String source = body.getOrDefault("source", "ko");
        String target = body.getOrDefault("target", "en");

        if (text.isBlank()) {
            return ResponseEntity.ok(Map.of("translatedText", ""));
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("X-NCP-APIGW-API-KEY-ID", CLIENT_ID);
            headers.set("X-NCP-APIGW-API-KEY",    CLIENT_SECRET);

            String reqBody = "source=" + source + "&target=" + target
                    + "&text=" + java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8);

            HttpEntity<String> entity = new HttpEntity<>(reqBody, headers);
            ResponseEntity<Map> resp = restTemplate.exchange(
                    PAPAGO_URL, HttpMethod.POST, entity, Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>)
                    ((Map<String, Object>) resp.getBody().get("message")).get("result");

            String translated = (String) message.get("translatedText");
            return ResponseEntity.ok(Map.of("translatedText", translated));

        } catch (Exception e) {
            // 번역 실패 시 에러 로그 + 원본 반환
            System.err.println("❌ 파파고 번역 실패: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.ok(Map.of("translatedText", text, "error", e.getMessage()));
        }
    }
}