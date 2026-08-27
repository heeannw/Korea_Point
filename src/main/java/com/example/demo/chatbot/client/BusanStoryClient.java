package com.example.demo.chatbot.client;

import com.example.demo.chatbot.config.BusanStoryProperties;
import com.example.demo.chatbot.dto.BusanStoryDto;
import com.example.demo.trip.integration.ExpiringCache;
import com.example.demo.trip.integration.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 부산광역시 스토리텔링 원형 정보 서비스를 호출한다. 챗봇의 RAG 근거 자료로만 쓰고,
 * 원문은 절대 DB에 저장하지 않는다 — 항상 이 클라이언트를 통해 그때그때 다시 받아온다.
 * 목록 전체는 몇 시간 정도 메모리에만 캐시해 호출 한도를 아낀다(영속 저장이 아니다).
 */
@Component
public class BusanStoryClient {

    private static final Logger log = LoggerFactory.getLogger(BusanStoryClient.class);
    private static final String CACHE_KEY = "all";
    private static final int MAX_PAGES = 20;
    private static final int CONNECT_TIMEOUT_MS = 2500;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final Pattern ATCH_FILE_ID = Pattern.compile("atchFileId=([A-Za-z0-9_]+)");

    private final RestTemplate restTemplate;
    private final BusanStoryProperties properties;
    private final ObjectMapper objectMapper;
    private final ExpiringCache<String, List<BusanStoryDto>> cache = new ExpiringCache<>();

    public BusanStoryClient(BusanStoryProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = ExternalApiClient.create(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 임베딩용 원문을 만들 때 전체 이야기를 순회하는 용도. 캐시가 비어 있으면 API를 호출해 채운다.
     */
    public List<BusanStoryDto> findAll() {
        return cache.get(CACHE_KEY).orElseGet(() -> {
            List<BusanStoryDto> stories = fetchAll();
            cache.put(CACHE_KEY, stories, Duration.ofMinutes(properties.getCacheMinutes()));
            return stories;
        });
    }

    /**
     * source_id(sourceKey)로 이야기 원문을 다시 찾는다. 이 API는 단건 조회를 지원하지 않아서
     * 전체 목록(캐시됨)에서 같은 키를 가진 항목을 찾는 방식으로 재조회한다.
     */
    public Optional<BusanStoryDto> findBySourceKey(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) return Optional.empty();
        return findAll().stream()
                .filter(story -> sourceKey.equals(sourceKey(story)))
                .findFirst();
    }

    /**
     * 이야기 하나를 가리키는 안정적인 키를 만든다. imgUrl에 박힌 첨부파일ID가 있으면 그걸 쓰고,
     * 없으면 제목+주소 조합으로 대체한다. 이 값은 DB엔 저장해도(원문이 아니라 "어디를 가리키는지"이므로) 된다.
     */
    public String sourceKey(BusanStoryDto story) {
        Matcher matcher = ATCH_FILE_ID.matcher(story.getImgUrl() == null ? "" : story.getImgUrl());
        if (matcher.find()) return matcher.group(1);
        return (story.getTitle() == null ? "" : story.getTitle())
                + "|" + (story.getAddr() == null ? "" : story.getAddr());
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
        // serviceKey를 직접 percent-encode한다. UriComponentsBuilder의 encode()는
        // '+'를 쿼리에서 이미 합법적인 문자로 보고 그대로 두는데, data.go.kr 서버는
        // 쿼리스트링을 application/x-www-form-urlencoded로 해석해 '+'를 공백으로 읽어
        // 인증키(디코딩 키)에 '+'가 섞여 있으면 키가 깨진다.
        String encodedKey = URLEncoder.encode(properties.getServiceKey(), StandardCharsets.UTF_8);
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl())
                .queryParam("serviceKey", encodedKey)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .queryParam("resultType", "json");
        URI encoded = uri.build(true).toUri();
        String responseBody = restTemplate.getForObject(encoded, String.class);
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new ChatbotApiException("부산 스토리텔링 응답 파싱에 실패했습니다.", e);
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
