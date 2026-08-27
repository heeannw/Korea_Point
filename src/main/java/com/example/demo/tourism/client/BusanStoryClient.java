package com.example.demo.tourism.client;

import com.example.demo.tourism.config.BusanStoryProperties;
import com.example.demo.tourism.dto.BusanStoryDto;
import com.example.demo.tourism.service.TourismService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.demo.trip.integration.ExpiringCache;

/**
 * 부산광역시 스토리텔링 원형 정보 서비스(설화·유래 등 장소 기반 이야기)를 조회한다.
 * TourAPI와 달리 있으면 상세 페이지를 풍부하게 해주는 보조 콘텐츠일 뿐이라,
 * 호출이 실패해도 예외를 던지지 않고 빈 목록으로 넘어간다.
 */
@Component
public class BusanStoryClient {

    private static final Logger log = LoggerFactory.getLogger(BusanStoryClient.class);
    private static final String CACHE_KEY = "all";
    /** 응답이 비정상적으로 계속 이어지는 경우를 대비한 안전장치. */
    private static final int MAX_PAGES = 20;

    private final RestClient restClient;
    private final BusanStoryProperties properties;
    private final ObjectMapper objectMapper;
    private final ExpiringCache<String, List<BusanStoryDto>> cache = new ExpiringCache<>();

    public BusanStoryClient(RestClient.Builder restClientBuilder,
                            BusanStoryProperties properties,
                            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 주어진 좌표에서 설정된 반경 안에 있는 이야기 중 가장 가까운 것을 찾는다.
     * 좌표가 없거나 매칭되는 이야기가 없으면 빈 값을 반환한다.
     */
    public Optional<BusanStoryDto> findNearestStory(Double lat, Double lng) {
        if (lat == null || lng == null) return Optional.empty();

        double radiusKm = properties.getMatchRadiusMeters() / 1000.0;
        BusanStoryDto nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (BusanStoryDto story : findAll()) {
            if (story.getLat() == null || story.getLng() == null) continue;
            double distance = TourismService.haversine(lat, lng, story.getLat(), story.getLng());
            if (distance <= radiusKm && distance < nearestDistance) {
                nearest = story;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** 전체 이야기 목록을 캐시에서 가져오고, 없으면 API를 호출해 채운다. */
    public List<BusanStoryDto> findAll() {
        return cache.get(CACHE_KEY).orElseGet(() -> {
            List<BusanStoryDto> stories = fetchAll();
            cache.put(CACHE_KEY, stories, Duration.ofMinutes(properties.getCacheMinutes()));
            return stories;
        });
    }

    private List<BusanStoryDto> fetchAll() {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) {
            log.warn("BUSAN_STORY_SERVICE_KEY가 없어 스토리텔링 콘텐츠를 건너뜁니다.");
            return List.of();
        }

        List<BusanStoryDto> all = new ArrayList<>();
        try {
            int rows = Math.max(1, properties.getRowsPerPage());
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode root = requestPage(page, rows);
                JsonNode body = root.path("getBusanCultureStoryTelling");
                all.addAll(mapItems(body.path("item")));

                long totalCount = body.path("totalCount").asLong(0);
                if ((long) page * rows >= totalCount) break;
            }
        } catch (Exception e) {
            log.warn("부산 스토리텔링 API 호출에 실패했습니다: {}", e.getMessage());
            return List.of();
        }
        return List.copyOf(all);
    }

    private JsonNode requestPage(int pageNo, int numOfRows) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .queryParam("serviceKey", properties.getServiceKey())
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .queryParam("resultType", "json");
        URI encoded = uri.build().encode().toUri();
        String responseBody = restClient.get().uri(encoded).retrieve().body(String.class);
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new TourApiException("부산 스토리텔링 응답 파싱에 실패했습니다.", e);
        }
    }

    private List<BusanStoryDto> mapItems(JsonNode items) {
        if (items.isMissingNode() || items.isNull()) return List.of();
        List<BusanStoryDto> result = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) addItem(result, item);
        } else if (items.isObject()) {
            addItem(result, items);
        }
        return result;
    }

    private void addItem(List<BusanStoryDto> result, JsonNode item) {
        try {
            result.add(objectMapper.treeToValue(item, BusanStoryDto.class));
        } catch (Exception e) {
            log.warn("스토리텔링 항목 하나를 읽지 못해 건너뜁니다: {}", e.getMessage());
        }
    }
}
