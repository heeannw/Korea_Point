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
import org.springframework.web.client.HttpClientErrorException;
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

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent";
    /** 429(요청 한도 초과)를 만나면 이만큼 재시도한다. 분당 한도라면 대기 후 회복되지만, 일일/결제 한도라면 재시도해도 계속 실패한다. */
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 3000;

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
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = String.format(ENDPOINT, model);

        int attempt = 0;
        while (true) {
            try {
                JsonNode root = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class).getBody();
                JsonNode values = root == null ? null : root.at("/embedding/values");
                if (values == null || !values.isArray() || values.isEmpty()) {
                    throw new ChatbotApiException("Gemini 임베딩 응답에 벡터 값이 없습니다.");
                }
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) vector[i] = (float) values.get(i).asDouble();
                return vector;
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    throw new ChatbotApiException(
                            "Gemini 임베딩 호출 한도를 초과했습니다(" + MAX_RETRIES + "회 재시도 후에도 실패). "
                                    + "분당 한도가 아니라 일일/결제 한도일 수 있으니 Google AI Studio에서 사용량을 확인하세요.", e);
                }
                long backoffMs = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
                log.warn("Gemini 임베딩 429(한도 초과), {}ms 대기 후 재시도 ({}/{}차)", backoffMs, attempt, MAX_RETRIES);
                sleep(backoffMs);
            } catch (RestClientException e) {
                throw new ChatbotApiException("Gemini 임베딩 호출에 실패했습니다: " + e.getMessage(), e);
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
