package com.example.demo.trip.weather;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.time.Duration;
import com.example.demo.trip.integration.ExpiringCache;

@Slf4j
@Service
public class AirQualityService {
    private final RestTemplate restTemplate;
    private final ExpiringCache<String, Optional<AirQuality>> cache = new ExpiringCache<>();

    @Value("${weather.air-quality.base-url:https://apis.data.go.kr/B552584/ArpltnInforInqireSvc}") private String baseUrl;
    @Value("${weather.service-key:}") private String serviceKey;
    @Value("${external-api.air-cache-minutes:10}") private long cacheMinutes;

    public AirQualityService(@Qualifier("tripExternalRestTemplate") RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public Optional<AirQuality> currentBusan() {
        if (serviceKey == null || serviceKey.isBlank()) return Optional.empty();
        Optional<Optional<AirQuality>> cached = cache.get("busan");
        if (cached.isPresent()) return cached.get();
        var uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/getCtprvnRltmMesureDnsty")
                .queryParam("serviceKey", serviceKey).queryParam("returnType", "json")
                .queryParam("numOfRows", 100).queryParam("pageNo", 1).queryParam("sidoName", "%EB%B6%80%EC%82%B0")
                .queryParam("ver", "1.4").build(true).toUri();
        try {
            JsonNode items = restTemplate.getForObject(uri, JsonNode.class);
            items = items == null ? null : items.at("/response/body/items");
            if (items == null || !items.isArray() || items.isEmpty()) return Optional.empty();
            int count = 0, pm10Total = 0, pm25Total = 0, worst10 = 0, worst25 = 0;
            String time = "";
            for (JsonNode item : items) {
                int pm10 = positiveInt(item.path("pm10Value").asText());
                int pm25 = positiveInt(item.path("pm25Value").asText());
                if (pm10 <= 0 && pm25 <= 0) continue;
                pm10Total += pm10; pm25Total += pm25; count++;
                worst10 = Math.max(worst10, positiveInt(item.path("pm10Grade1h").asText()));
                worst25 = Math.max(worst25, positiveInt(item.path("pm25Grade1h").asText()));
                if (time.isBlank()) time = item.path("dataTime").asText("");
            }
            Optional<AirQuality> result = count == 0 ? Optional.empty() : Optional.of(new AirQuality(pm10Total / count, pm25Total / count, worst10, worst25, time));
            cache.put("busan", result, Duration.ofMinutes(cacheMinutes));
            return result;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("AirKorea request failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private int positiveInt(String value) { try { return Math.max(0, Integer.parseInt(value)); } catch (Exception e) { return 0; } }
}
