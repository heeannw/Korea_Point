package com.example.demo.tourism.ai;

import com.example.demo.tourism.dto.PersonalRecoDto;
import com.example.demo.tourism.dto.UserTasteProfile;
import com.example.demo.trip.integration.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * "나를 위한 추천" 각 항목에 자연스러운 한 문장 근거를 붙인다.
 *
 * <p>추천 장소 선정은 {@code PersonalRecommendService}(규칙 엔진)가 이미 끝냈다.
 * 여기서는 <b>선정된 5곳 + 규칙 근거만</b> Gemini 에 넘겨 설명 문장만 받는다.
 * 키가 없거나 실패하면 규칙 근거 태그를 그대로 조립해서 쓴다.</p>
 *
 * <p>캐시 키 = 취향 프로필 + 추천 contentId 목록. 프로필이 안 바뀌면 재호출하지 않는다.</p>
 */
@Service
public class GeminiRecoReasonService {

    private static final Logger log = LoggerFactory.getLogger(GeminiRecoReasonService.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    /** 관광 추천 설명 전용 키(GEMINI_API_tour_KEY). 없으면 공용 키로 폴백 — application.properties 참고. */
    @Value("${ai.gemini.tour-api-key:}")
    private String apiKey;

    @Value("${ai.gemini.model:gemini-flash-latest}")
    private String model;

    private final RestTemplate restTemplate = ExternalApiClient.create(3000, 60000);
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public List<PersonalRecoDto> explain(UserTasteProfile profile, List<PersonalRecoDto> recos) {
        if (recos.isEmpty()) return recos;

        String key = cacheKey(profile, recos);
        List<String> lines = cache.get(key);
        if (lines == null) {
            lines = isConfigured() ? tryGemini(profile, recos) : null;
            if (lines == null || lines.size() != recos.size()) lines = fallback(recos);
            if (cache.size() < 500) cache.put(key, lines);
        }

        List<PersonalRecoDto> out = new ArrayList<>(recos.size());
        for (int i = 0; i < recos.size(); i++) {
            out.add(recos.get(i).withGenReason(lines.get(i)));
        }
        return out;
    }

    private List<String> tryGemini(UserTasteProfile p, List<PersonalRecoDto> recos) {
        String taste = "선호 카테고리: " + topKeys(p.typeWeight())
                + " / 관심 지역: " + topKeys(p.areaWeight())
                + " / 관심 유형: " + topKeys(p.detailWeight());
        StringBuilder places = new StringBuilder();
        for (int i = 0; i < recos.size(); i++) {
            PersonalRecoDto r = recos.get(i);
            places.append(i + 1).append(". ").append(r.title())
                  .append(" (근거: ").append(String.join(", ", r.reasons())).append(")\n");
        }
        String prompt = """
                다음은 규칙 추천 엔진이 사용자 취향에 맞춰 고른 부산 관광지 목록이다.
                장소를 추가하거나 순서를 바꾸지 말고, 각 장소를 왜 추천했는지 사용자 취향과 엮어
                한국어로 딱 한 문장씩 만들어라. 존댓말, 20자~45자. 장소명은 문장에 안 넣어도 된다.
                출력은 "1. 문장" 형식으로 %d줄만.

                [사용자 취향]
                %s

                [추천 목록]
                %s""".formatted(recos.size(), taste, places);

        try {
            return parseNumbered(callGemini(prompt), recos.size());
        } catch (Exception e) {
            log.debug("추천 근거 Gemini 생성 실패 — 규칙 태그로 폴백", e);
            return null;
        }
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** trip.ai.GeminiTripSummaryService 와 같은 방식의 단발 호출. 실패는 호출측이 폴백 처리. */
    private String callGemini(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        // gemini-3.6-flash는 "thinking" 토큰도 maxOutputTokens 예산을 함께 써서, 넉넉히 안 잡으면
        // 최종 답변이 잘리거나 비어서 온다(GeminiChatClient 와 동일한 이유).
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 8192));
        JsonNode root = restTemplate.exchange(String.format(ENDPOINT, model),
                HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class).getBody();
        return root == null ? "" : root.at("/candidates/0/content/parts/0/text").asText("").trim();
    }

    private static List<String> fallback(List<PersonalRecoDto> recos) {
        return recos.stream()
                .map(r -> String.join(" · ", r.reasons()) + " 기반 추천")
                .collect(Collectors.toList());
    }

    private static List<String> parseNumbered(String text, int expected) {
        if (text == null || text.isBlank()) return null;
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String s = raw.strip().replaceFirst("^\\s*\\d+[.)]\\s*", "").strip();
            if (!s.isEmpty()) lines.add(s);
        }
        if (lines.size() < expected) return null;
        return lines.subList(0, expected);
    }

    private static String topKeys(Map<String, Double> m) {
        return m.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    private static String cacheKey(UserTasteProfile p, List<PersonalRecoDto> recos) {
        String prof = stable(p.typeWeight()) + "|" + stable(p.areaWeight()) + "|" + stable(p.detailWeight());
        String ids = recos.stream().map(PersonalRecoDto::contentId).collect(Collectors.joining(","));
        return Integer.toHexString((prof + "#" + ids).hashCode());
    }

    private static String stable(Map<String, Double> m) {
        return new TreeMap<>(m).entrySet().stream()
                .map(e -> e.getKey() + ":" + Math.round(e.getValue() * 100))
                .collect(Collectors.joining(";"));
    }
}
