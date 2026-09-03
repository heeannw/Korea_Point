package com.example.demo.trip.ai;

import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.time.Duration;
import com.example.demo.trip.integration.ExpiringCache;

@Slf4j
@Service
public class GeminiTripSummaryService {
    private final RestTemplate restTemplate;
    private final ExpiringCache<String, String> cache = new ExpiringCache<>();

    @Value("${ai.gemini.api-key:}") private String apiKey;
    @Value("${ai.gemini.model:gemini-flash-latest}") private String model;
    @Value("${external-api.gemini-cache-minutes:60}") private long cacheMinutes;

    public GeminiTripSummaryService(@Qualifier("tripExternalRestTemplate") RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public String summarize(TripRecommendRequest request, TripRecommendResponse response) {
        if (apiKey == null || apiKey.isBlank()) return fallback(response);
        StringBuilder itinerary = new StringBuilder();
        response.getSchedule().forEach(day -> {
            itinerary.append("Day ").append(day.getDay()).append(": ");
            day.getSlots().forEach(slot -> itinerary.append(slot.getTime()).append(' ').append(slot.getPlaceName())
                    .append("(").append(slot.getReason()).append("), "));
        });
        String styleDescription = request.getStyleDescription();
        String styleNote = (styleDescription != null && !styleDescription.isBlank())
                ? " 사용자가 이렇게 자유롭게 요청했다: \"" + styleDescription + "\" — 이미 확정된 일정 안에서 이 요청과 맞닿는 부분이 있으면 자연스럽게 짚어줘라(없으면 굳이 언급하지 않아도 된다)."
                : "";
        String prompt = "다음은 규칙 엔진이 확정한 부산 여행 일정이다. 장소를 추가하거나 순서를 바꾸지 말고, " +
                "날씨 반영 내용과 이동 효율을 중심으로 한국어 2~3문장으로 친절하게 요약해라." + styleNote + " 일정: " + itinerary;
        String result = callGemini(prompt, 220, Duration.ofMinutes(cacheMinutes));
        return result.isBlank() ? fallback(response) : result;
    }

    /** 캐시 확인 → Gemini 호출 → 캐시 저장까지 공통 처리. 실패 시 빈 문자열. */
    private String callGemini(String prompt, int maxOutputTokens, Duration cacheTtl) {
        String cacheKey = Integer.toHexString(prompt.hashCode());
        Optional<String> cached = cache.get(cacheKey);
        if (cached.isPresent()) return cached.get();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", maxOutputTokens));
        try {
            JsonNode root = restTemplate.exchange("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent",
                    HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class).getBody();
            String text = root == null ? "" : root.at("/candidates/0/content/parts/0/text").asText("").trim();
            if (!text.isBlank()) cache.put(cacheKey, text, cacheTtl);
            return text;
        } catch (RestClientException e) {
            log.warn("Gemini 호출 실패: {}", e.getMessage());
            return "";
        }
    }

    private String fallback(TripRecommendResponse response) {
        boolean weatherAdjusted = response.getSchedule().stream().flatMap(d -> d.getSlots().stream())
                .anyMatch(s -> s.getReason() != null && (s.getReason().contains("비 예보") || s.getReason().contains("악천후")));
        return weatherAdjusted ? "날씨 예보를 반영해 실내 장소를 우선하고 이동 거리를 줄인 일정입니다. 출발 전에 최신 예보를 한 번 더 확인해 주세요."
                : "선호 장소와 이동 거리를 함께 고려해 효율적으로 구성한 부산 일정입니다.";
    }
}
