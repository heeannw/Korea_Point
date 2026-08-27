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
 * Google Gemini 임베딩 API로 텍스트를 벡터로 바꾼다. 트립 모듈의 GeminiTripSummaryService와
 * 같은 Gemini API 키를 쓴다(같은 벤더 계정, 새 키 발급 불필요).
 * gemini-embedding-001은 기본 3072차원이라, outputDimensionality로 768차원으로 줄여
 * vector(768) 컬럼과 맞춘다(실측 확인: 앞 768개 값이 3072차원 결과의 앞부분과 그대로 일치).
 */
@Component
public class GeminiEmbeddingClient {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent";

    private final RestTemplate restTemplate;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${chatbot.embedding.model:gemini-embedding-001}")
    private String model;

    @Value("${chatbot.embedding.dimension:768}")
    private int dimension;

    public GeminiEmbeddingClient() {
        this.restTemplate = ExternalApiClient.create(3000, 8000);
    }

    /** 텍스트 하나를 임베딩 벡터로 변환한다. 결과는 DB에 저장해도 되지만(벡터일 뿐 원문이 아님), 입력 텍스트는 어디에도 저장하지 않는다. */
    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ChatbotApiException("GEMINI_API_KEY가 없어 임베딩을 만들 수 없습니다.");
        }
        if (text == null || text.isBlank()) {
            throw new ChatbotApiException("임베딩할 텍스트가 비어 있습니다.");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> body = Map.of(
                "model", "models/" + model,
                "content", Map.of("parts", List.of(Map.of("text", text))),
                "outputDimensionality", dimension);

        try {
            JsonNode root = restTemplate.exchange(
                    String.format(ENDPOINT, model),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class).getBody();
            JsonNode values = root == null ? null : root.at("/embedding/values");
            if (values == null || !values.isArray() || values.isEmpty()) {
                throw new ChatbotApiException("Gemini 임베딩 응답에 벡터 값이 없습니다.");
            }
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) vector[i] = (float) values.get(i).asDouble();
            return vector;
        } catch (RestClientException e) {
            throw new ChatbotApiException("Gemini 임베딩 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
