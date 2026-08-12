package com.example.demo.trip.weather;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.example.demo.trip.integration.ExpiringCache;

@Slf4j
@Service
public class WeatherForecastService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final List<Integer> BASE_HOURS = List.of(2, 5, 8, 11, 14, 17, 20, 23);

    private final RestTemplate restTemplate;
    private final Clock clock;
    private final ExpiringCache<String, Map<LocalDateTime, WeatherForecast>> cache = new ExpiringCache<>();

    @Value("${weather.forecast.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}")
    private String baseUrl;

    @Value("${weather.service-key:}")
    private String serviceKey;

    @Value("${external-api.weather-cache-minutes:30}") private long cacheMinutes;

    @Autowired
    public WeatherForecastService(@Qualifier("tripExternalRestTemplate") RestTemplate restTemplate) {
        this(restTemplate, Clock.system(KOREA));
    }

    WeatherForecastService(RestTemplate restTemplate, Clock clock) {
        this.restTemplate = restTemplate;
        this.clock = clock;
    }

    public Map<LocalDateTime, WeatherForecast> getForecast(double latitude, double longitude) {
        if (serviceKey == null || serviceKey.isBlank()) return Map.of();

        BaseTime base = latestPublishedBase(ZonedDateTime.now(clock));
        Grid grid = toGrid(latitude, longitude);
        String cacheKey = base.date() + ":" + base.hour() + ":" + grid.x() + ":" + grid.y();
        Optional<Map<LocalDateTime, WeatherForecast>> cached = cache.get(cacheKey);
        if (cached.isPresent()) return cached.get();
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/getVilageFcst")
                .queryParam("serviceKey", serviceKey)
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 2000)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", base.date().format(DATE))
                .queryParam("base_time", String.format("%02d00", base.hour()))
                .queryParam("nx", grid.x())
                .queryParam("ny", grid.y())
                .build(true).toUri();
        try {
            Map<LocalDateTime, WeatherForecast> result = parse(restTemplate.getForObject(uri, JsonNode.class));
            if (!result.isEmpty()) cache.put(cacheKey, result, Duration.ofMinutes(cacheMinutes));
            return result;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Weather forecast request failed for grid ({}, {}): {}", grid.x(), grid.y(), e.getMessage());
            return Map.of();
        }
    }

    public Map<LocalDateTime, WeatherForecast> getUltraForecast(double latitude, double longitude) {
        if (serviceKey == null || serviceKey.isBlank()) return Map.of();
        ZonedDateTime available = ZonedDateTime.now(clock).minusMinutes(45);
        Grid grid = toGrid(latitude, longitude);
        String date = available.format(DATE), time = String.format("%02d30", available.getHour());
        String cacheKey = "ultra:" + date + ":" + time + ":" + grid.x() + ":" + grid.y();
        var cached = cache.get(cacheKey); if (cached.isPresent()) return cached.get();
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/getUltraSrtFcst")
                .queryParam("serviceKey", serviceKey).queryParam("pageNo", 1).queryParam("numOfRows", 1000)
                .queryParam("dataType", "JSON").queryParam("base_date", date).queryParam("base_time", time)
                .queryParam("nx", grid.x()).queryParam("ny", grid.y()).build(true).toUri();
        try {
            Map<LocalDateTime, WeatherForecast> result = parse(restTemplate.getForObject(uri, JsonNode.class));
            if (!result.isEmpty()) cache.put(cacheKey, result, Duration.ofMinutes(10));
            return result;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Ultra-short forecast request failed: {}", e.getMessage()); return Map.of();
        }
    }

    public String forecastIssuedAt() {
        BaseTime base = latestPublishedBase(ZonedDateTime.now(clock));
        return base.date().format(DateTimeFormatter.ISO_DATE) + " " + String.format("%02d:00", base.hour());
    }

    private Map<LocalDateTime, WeatherForecast> parse(JsonNode root) {
        JsonNode items = root == null ? null : root.at("/response/body/items/item");
        if (items == null || !items.isArray()) return Map.of();

        Map<LocalDateTime, Map<String, String>> values = new HashMap<>();
        items.forEach(item -> {
            try {
                LocalDate date = LocalDate.parse(item.path("fcstDate").asText(), DATE);
                String time = item.path("fcstTime").asText("0000");
                LocalDateTime at = date.atTime(Integer.parseInt(time.substring(0, 2)), Integer.parseInt(time.substring(2, 4)));
                values.computeIfAbsent(at, ignored -> new HashMap<>())
                        .put(normalizeCategory(item.path("category").asText()), item.path("fcstValue").asText());
            } catch (RuntimeException ignored) {
                // Ignore malformed rows while retaining the rest of the forecast.
            }
        });

        Map<LocalDateTime, WeatherForecast> forecasts = new HashMap<>();
        values.forEach((at, row) -> forecasts.put(at, new WeatherForecast(
                at, number(row.get("TMP")), integer(row.get("POP")), precipitationType(row.get("PTY")),
                precipitation(row.get("PCP")), number(row.get("WSD")), sky(row.get("SKY")))));
        return forecasts;
    }

    public Optional<WeatherForecast> nearest(Map<LocalDateTime, WeatherForecast> forecasts, LocalDateTime at) {
        if (forecasts == null || forecasts.isEmpty() || at == null) return Optional.empty();
        return forecasts.values().stream()
                .filter(f -> Math.abs(Duration.between(at, f.forecastAt()).toHours()) <= 2)
                .min(Comparator.comparingLong(f -> Math.abs(Duration.between(at, f.forecastAt()).toMinutes())));
    }

    static Grid toGrid(double lat, double lon) {
        double re = 6371.00877 / 5.0;
        double slat1 = Math.toRadians(30.0), slat2 = Math.toRadians(60.0);
        double olon = Math.toRadians(126.0), olat = Math.toRadians(38.0);
        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) /
                Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5));
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);
        double ra = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + Math.toRadians(lat) * 0.5), sn);
        double theta = Math.toRadians(lon) - olon;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;
        return new Grid((int) Math.floor(ra * Math.sin(theta) + 43 + 0.5),
                (int) Math.floor(ro - ra * Math.cos(theta) + 136 + 0.5));
    }

    private BaseTime latestPublishedBase(ZonedDateTime now) {
        ZonedDateTime available = now.minusMinutes(10);
        int hour = BASE_HOURS.stream().filter(h -> h <= available.getHour()).reduce((a, b) -> b).orElse(23);
        LocalDate date = available.toLocalDate();
        if (available.getHour() < 2) date = date.minusDays(1);
        return new BaseTime(date, hour);
    }

    private double number(String value) { try { return Double.parseDouble(value); } catch (Exception e) { return 0; } }
    private int integer(String value) { return (int) number(value); }
    private double precipitation(String value) {
        if (value == null || value.contains("없음")) return 0;
        String numeric = value.replaceAll("[^0-9.]", "");
        return number(numeric);
    }
    private String precipitationType(String value) {
        return switch (value == null ? "0" : value) { case "1" -> "RAIN"; case "2" -> "RAIN_SNOW"; case "3" -> "SNOW"; case "4" -> "SHOWER"; default -> "NONE"; };
    }
    private String sky(String value) { return switch (value == null ? "" : value) { case "1" -> "CLEAR"; case "3" -> "CLOUDY"; case "4" -> "OVERCAST"; default -> "UNKNOWN"; }; }
    private String normalizeCategory(String category) { return switch (category) { case "T1H" -> "TMP"; case "RN1" -> "PCP"; default -> category; }; }

    record Grid(int x, int y) {}
    private record BaseTime(LocalDate date, int hour) {}
}
