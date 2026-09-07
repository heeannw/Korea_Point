package com.example.demo.chatbot.client;

import com.example.demo.trip.integration.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 프롬프트를 Gemini에 보내 답변 텍스트를 받는다. trip.ai.GeminiTripSummaryService와 같은
 * Gemini API 키/모델(gemini-flash-latest)을 그대로 재사용한다.
 */
@Component
public class GeminiChatClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatClient.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    /** 503(일시적 과부하)은 구글 측이 "잠시 후 재시도"를 권장하는 케이스라 자동 재시도한다. */
    private static final int MAX_RETRIES = 4;
    private static final long INITIAL_BACKOFF_MS = 2000;

    private final RestTemplate restTemplate;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-flash-latest}")
    private String model;

    public GeminiChatClient() {
        // 무료 등급이라 응답이 느릴 때가 있어 read timeout을 넉넉히 잡는다.
        this.restTemplate = ExternalApiClient.create(3000, 60000);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String generate(String prompt) {
        return generate(prompt, null, null);
    }

    /** imageBase64/imageMimeType이 있으면 텍스트 프롬프트와 같은 요청 안에 이미지 part를
     *  같이 실어 보낸다(Gemini 멀티모달 — inline_data). 이미지가 없으면 기존과 동일하게 텍스트만. */
    public String generate(String prompt, String imageBase64, String imageMimeType) {
        if (!isConfigured()) {
            throw new ChatbotApiException("GEMINI_API_KEY가 없어 답변을 생성할 수 없습니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", prompt));
        if (imageBase64 != null && !imageBase64.isBlank()) {
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", (imageMimeType == null || imageMimeType.isBlank()) ? "image/jpeg" : imageMimeType,
                    "data", imageBase64)));
        }

        // gemini-3.6-flash는 "생각(thinking)" 과정도 maxOutputTokens 예산을 같이 쓰는 모델이라
        // 최종 답변 전에 예산이 소진되면 문장이 끊긴다. 여유 있게 넉넉히 잡는다.
        // 답변 생성 요청이 번역 언어일 때는 참고 자료 본문 전체를 같이 번역해서 돌려받다 보니
        // 응답이 꽤 커질 수 있어(원문 여러 개 + 번역본) 예산을 넉넉히 잡는다.
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 8192));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format(ENDPOINT, model);

        int attempt = 0;
        while (true) {
            try {
                JsonNode root = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class).getBody();
                String text = root == null ? "" : root.at("/candidates/0/content/parts/0/text").asText("").trim();
                if (text.isBlank()) {
                    throw new ChatbotApiException("Gemini 응답에 답변 텍스트가 없습니다.");
                }
                return text;
            } catch (HttpServerErrorException.ServiceUnavailable e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    throw new ChatbotApiException(
                            "Gemini 모델이 계속 과부하 상태입니다(" + MAX_RETRIES + "회 재시도 후에도 503). 잠시 후 다시 시도해 주세요.", e);
                }
                long backoffMs = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                log.warn("Gemini 503(모델 과부하), {}ms 대기 후 재시도 ({}/{}차)", backoffMs, attempt, MAX_RETRIES);
                sleep(backoffMs);
            } catch (RestClientException e) {
                throw new ChatbotApiException("Gemini 답변 생성 호출에 실패했습니다: " + e.getMessage(), e);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatbotApiException("재시도 대기 중 인터럽트되었습니다.", e);
        }
    }
}
