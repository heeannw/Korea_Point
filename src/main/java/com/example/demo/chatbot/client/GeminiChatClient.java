package com.example.demo.chatbot.client;

import com.example.demo.trip.integration.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 프롬프트를 Gemini에 보내 답변 텍스트를 받는다. trip.ai.GeminiTripSummaryService와 같은
 * Gemini API 키/모델(gemini-flash-latest)을 그대로 재사용한다.
 */
@Component
public class GeminiChatClient {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final RestTemplate restTemplate;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-flash-latest}")
    private String model;

    public GeminiChatClient() {
        this.restTemplate = ExternalApiClient.create(3000, 15000);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String generate(String prompt) {
        if (!isConfigured()) {
            throw new ChatbotApiException("GEMINI_API_KEY가 없어 답변을 생성할 수 없습니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 800));

        try {
            JsonNode root = restTemplate.exchange(
                    String.format(ENDPOINT, model),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class).getBody();
            String text = root == null ? "" : root.at("/candidates/0/content/parts/0/text").asText("").trim();
            if (text.isBlank()) {
                throw new ChatbotApiException("Gemini 응답에 답변 텍스트가 없습니다.");
            }
            return text;
        } catch (RestClientException e) {
            throw new ChatbotApiException("Gemini 답변 생성 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
