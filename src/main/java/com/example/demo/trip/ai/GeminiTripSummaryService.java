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

    /** 세션 언어 코드 → 프롬프트에 넣을 언어 이름. 관광 페이지의 DeepL처럼 사후 번역을 붙이는 대신,
     *  Gemini에게 처음부터 그 언어로 요약을 쓰게 시키는 편이 이중 번역을 피할 수 있어 더 자연스럽다. */
    private static final Map<String, String> LANG_NAMES = Map.of(
            "en", "English", "ja", "日本語(Japanese)", "zh", "中文(Chinese)", "fr", "français(French)"
    );

    public String summarize(TripRecommendRequest request, TripRecommendResponse response) {
        String lang = request.getLang();
        if (apiKey == null || apiKey.isBlank()) return fallback(response, lang);
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
        String langName = LANG_NAMES.get(lang == null ? "" : lang.toLowerCase());
        String langNote = langName != null
                ? " 반드시 " + langName + "로만 답해라(다른 언어를 섞지 마라)."
                : " 한국어로 답해라.";
        String prompt = "다음은 규칙 엔진이 확정한 부산 여행 일정이다. 장소를 추가하거나 순서를 바꾸지 말고, " +
                "날씨 반영 내용과 이동 효율을 중심으로 2~3문장으로 친절하게 요약해라." + langNote + styleNote + " 일정: " + itinerary;
        String result = callGemini(prompt, 220, Duration.ofMinutes(cacheMinutes));
        return result.isBlank() ? fallback(response, lang) : result;
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

    /** Gemini API 키가 없거나 호출이 실패했을 때 쓰는 정적 문구 — 이 정도 짧은 고정 문구는
     *  굳이 API를 호출할 필요 없이 5개 언어로 직접 준비해두는 편이 낫다(관광 페이지의 화면
     *  문구가 DeepL 대신 메시지 키를 쓰는 것과 같은 이유). */
    private String fallback(TripRecommendResponse response, String lang) {
        boolean weatherAdjusted = response.getSchedule().stream().flatMap(d -> d.getSlots().stream())
                .anyMatch(s -> s.getReason() != null && (s.getReason().contains("비 예보") || s.getReason().contains("악천후")
                        || s.getReason().contains("rain forecast") || s.getReason().contains("bad weather")));
        String key = lang == null ? "ko" : lang.toLowerCase();
        return switch (key) {
            case "en" -> weatherAdjusted
                    ? "This itinerary favors indoor spots and shorter travel distances based on the weather forecast. Please check the latest forecast once more before you go."
                    : "A Busan itinerary efficiently arranged around your preferred places and travel distance.";
            case "ja" -> weatherAdjusted
                    ? "天気予報を反映して屋内の場所を優先し、移動距離を減らした日程です。出発前に最新の予報をもう一度ご確認ください。"
                    : "希望の場所と移動距離を考慮して効率的に構成した釜山日程です。";
            case "zh" -> weatherAdjusted
                    ? "根据天气预报优先安排了室内地点并缩短了移动距离。出发前请再次确认最新预报。"
                    : "综合考虑偏好地点和移动距离,高效安排的釜山行程。";
            case "fr" -> weatherAdjusted
                    ? "Cet itinéraire privilégie les lieux intérieurs et réduit les distances de trajet selon les prévisions météo. Vérifiez les dernières prévisions avant de partir."
                    : "Un itinéraire à Busan organisé efficacement autour de vos lieux préférés et des distances de trajet.";
            default -> weatherAdjusted
                    ? "날씨 예보를 반영해 실내 장소를 우선하고 이동 거리를 줄인 일정입니다. 출발 전에 최신 예보를 한 번 더 확인해 주세요."
                    : "선호 장소와 이동 거리를 함께 고려해 효율적으로 구성한 부산 일정입니다.";
        };
    }
}
