package com.example.demo.trip.weather;

import com.example.demo.trip.integration.ExpiringCache;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j @Service
public class HealthRiskService {
    private final RestTemplate restTemplate;
    private final ExpiringCache<String, HealthRisk> cache = new ExpiringCache<>();
    @Value("${weather.living-index.base-url}") private String livingUrl;
    @Value("${weather.impact.base-url}") private String impactUrl;
    @Value("${weather.service-key:}") private String serviceKey;

    public HealthRiskService(@Qualifier("tripExternalRestTemplate") RestTemplate restTemplate) { this.restTemplate = restTemplate; }

    public Optional<HealthRisk> currentBusan() {
        if (serviceKey == null || serviceKey.isBlank()) return Optional.empty();
        var cached = cache.get("busan"); if (cached.isPresent()) return cached;
        Integer uv = uvIndex();
        String heat = impact("/getHWImpactValueV2");
        String cold = impact("/getCWImpactValueV2");
        if (uv == null && heat.isBlank() && cold.isBlank()) return Optional.empty();
        HealthRisk result = new HealthRisk(uv, heat, cold);
        cache.put("busan", result, Duration.ofMinutes(30));
        return Optional.of(result);
    }

    private Integer uvIndex() {
        String time = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
        var uri = UriComponentsBuilder.fromHttpUrl(livingUrl + "/getUVIdxV5")
                .queryParam("serviceKey", serviceKey).queryParam("pageNo", 1).queryParam("numOfRows", 10)
                .queryParam("dataType", "JSON").queryParam("areaNo", "2600000000").queryParam("time", time).build(true).toUri();
        try {
            JsonNode item = restTemplate.getForObject(uri, JsonNode.class);
            item = item == null ? null : item.at("/response/body/items/item/0");
            if (item == null || item.isMissingNode()) return null;
            for (String key : List.of("h0", "h3", "today")) if (item.has(key)) return numeric(item.path(key).asText());
        } catch (RestClientException | IllegalArgumentException e) { log.warn("UV index request failed: {}", e.getMessage()); }
        return null;
    }

    private String impact(String path) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        var uri = UriComponentsBuilder.fromHttpUrl(impactUrl + path)
                .queryParam("serviceKey", serviceKey).queryParam("pageNo", 1).queryParam("numOfRows", 10)
                .queryParam("dataType", "JSON").queryParam("base_date", date).queryParam("regId", "2600000000").build(true).toUri();
        try {
            JsonNode root = restTemplate.getForObject(uri, JsonNode.class);
            JsonNode item = root == null ? null : root.at("/response/body/items/item/0");
            if (item == null || item.isMissingNode()) return "";
            for (String key : List.of("impact", "riskLevel", "lvl", "value")) if (item.has(key)) return item.path(key).asText("");
            return item.toString();
        } catch (RestClientException | IllegalArgumentException e) { log.warn("Impact forecast request failed: {}", e.getMessage()); return ""; }
    }
    private Integer numeric(String value) { try { return (int)Math.round(Double.parseDouble(value.replaceAll("[^0-9.]", ""))); } catch (Exception e) { return null; } }
}
