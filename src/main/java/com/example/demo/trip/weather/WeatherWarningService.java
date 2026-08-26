package com.example.demo.trip.weather;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import com.example.demo.trip.integration.ExpiringCache;

@Slf4j
@Service
public class WeatherWarningService {
    private final RestTemplate restTemplate;
    private final ExpiringCache<String, String> cache = new ExpiringCache<>();

    @Value("${weather.warning.base-url:https://apis.data.go.kr/1360000/WthrWrnInfoService}")
    private String baseUrl;
    @Value("${weather.service-key:}")
    private String serviceKey;
    @Value("${external-api.warning-cache-minutes:10}") private long cacheMinutes;

    public WeatherWarningService(@Qualifier("tripExternalRestTemplate") RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public String currentBusanWarning() {
        if (serviceKey == null || serviceKey.isBlank()) return "";
        var cached = cache.get("busan");
        if (cached.isPresent()) return "NONE".equals(cached.get()) ? "" : cached.get();
        String from = LocalDate.now().minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE) + "0000";
        String to = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "2359";
        var uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/getPwnStatus")
                .queryParam("serviceKey", serviceKey).queryParam("pageNo", 1).queryParam("numOfRows", 10)
                .queryParam("dataType", "JSON").queryParam("fromTmFc", from).queryParam("toTmFc", to)
                .build(true).toUri();
        try {
            JsonNode root = restTemplate.getForObject(uri, JsonNode.class);
            JsonNode items = root == null ? null : root.at("/response/body/items/item");
            if (items == null || items.isMissingNode()) { cache.put("busan", "NONE", Duration.ofMinutes(cacheMinutes)); return ""; }
            String text = items.toString();
            if (!text.contains("부산")) { cache.put("busan", "NONE", Duration.ofMinutes(cacheMinutes)); return ""; }
            for (String keyword : new String[]{"태풍", "호우", "강풍", "폭염", "한파", "풍랑"}) {
                if (text.contains(keyword)) { String result = "부산 지역 " + keyword + " 특보가 발표되어 야외 일정을 최소화했습니다."; cache.put("busan", result, Duration.ofMinutes(cacheMinutes)); return result; }
            }
            String result = "부산 지역 기상특보가 발표되어 야외 활동 전 최신 정보를 확인하세요.";
            cache.put("busan", result, Duration.ofMinutes(cacheMinutes)); return result;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Weather warning request failed: {}", e.getMessage());
            return "";
        }
    }
}
