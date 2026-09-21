package com.example.demo.chatbot.client;

import com.example.demo.chatbot.config.BusanStoryProperties;
import com.example.demo.chatbot.dto.BusanStoryDto;
import com.example.demo.trip.integration.ExpiringCache;
import com.example.demo.trip.integration.ExternalApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    /**
     * 저쪽 API 는 멀쩡하다가도 간헐적으로 504 를 낸다(실측: 같은 요청 20회 중 2회, 10초쯤 끌다가 실패).
     * 곧바로 다시 부르면 대개 성공해서 한 쪽당 이만큼 더 시도한다.
     */
    private static final int MAX_PAGE_RETRIES = 2;
    private static final long RETRY_BACKOFF_MS = 400;
    private static final Pattern ATCH_FILE_ID = Pattern.compile("atchFileId=([A-Za-z0-9_]+)");

    private final RestTemplate restTemplate;
    private final BusanStoryProperties properties;
    private final ObjectMapper objectMapper;
    private final ExpiringCache<String, List<BusanStoryDto>> cache = new ExpiringCache<>();
    /** 마지막으로 제대로 받아온 목록. 갱신에 실패했을 때 이걸로 버틴다 — 빈 목록으로 덮지 않는다. */
    private volatile List<BusanStoryDto> lastGood = List.of();

    // 생성자가 둘이라 스프링에게 어느 쪽으로 만들지 알려 줘야 한다. 빼면 기동 자체가 막힌다.
    @Autowired
    public BusanStoryClient(BusanStoryProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, ExternalApiClient.create(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS));
    }

    /** 시험에서 응답을 흉내 낼 수 있게 RestTemplate 을 바깥에서 넣는 통로. 운영 코드는 위 생성자를 쓴다. */
    BusanStoryClient(BusanStoryProperties properties, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 임베딩용 원문을 만들 때 전체 이야기를 순회하는 용도. 캐시가 비어 있으면 API를 호출해 채운다.
     */
    public List<BusanStoryDto> findAll() {
        Optional<List<BusanStoryDto>> cached = cache.get(CACHE_KEY);
        if (cached.isPresent()) return cached.get();

        Fetched fetched = fetchAll();
        if (!fetched.complete()) {
            // 실패는 절대 캐시하지 않는다. 빈 목록이나 반쪽짜리를 캐시하면 저쪽이 한 번 삐끗한 대가로
            // 캐시 수명(6시간) 내내 챗봇이 못 찾는다 — 그동안 API 를 다시 불러 보지도 않는다.
            // 캐시를 안 채우니 다음 질문이 곧바로 다시 시도한다.
            List<BusanStoryDto> best = fetched.stories().size() > lastGood.size() ? fetched.stories() : lastGood;
            log.warn("스토리텔링 목록을 다 받지 못했습니다(받은 것 {}건). 캐시하지 않고 {}건으로 답합니다.",
                    fetched.stories().size(), best.size());
            return best;
        }
        cache.put(CACHE_KEY, fetched.stories(), Duration.ofMinutes(properties.getCacheMinutes()));
        lastGood = fetched.stories();
        return fetched.stories();
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

    /** 받아 온 이야기와, 마지막 쪽까지 빠짐없이 받았는지. 반쪽짜리는 캐시하면 안 되니 구분해서 알린다. */
    private record Fetched(List<BusanStoryDto> stories, boolean complete) {}

    private Fetched fetchAll() {
        if (properties.getServiceKey() == null || properties.getServiceKey().isBlank()) {
            log.warn("BUSAN_STORY_SERVICE_KEY가 없어 스토리텔링 콘텐츠를 건너뜁니다.");
            return new Fetched(List.of(), false);
        }

        List<BusanStoryDto> all = new ArrayList<>();
        int rows = Math.max(1, properties.getRowsPerPage());
        for (int page = 1; page <= MAX_PAGES; page++) {
            JsonNode body = requestPageOrNull(page, rows);
            if (body == null) {
                // 한 쪽을 끝내 못 받았다. 앞서 받은 쪽까지는 살려서 넘긴다 — 전부 버리면 254건이 0건이 된다.
                log.warn("스토리텔링 {}쪽을 받지 못해 여기까지({}건)만 씁니다.", page, all.size());
                return new Fetched(List.copyOf(all), false);
            }
            all.addAll(mapItems(body.path("item")));

            long totalCount = body.path("totalCount").asLong(0);
            if ((long) page * rows >= totalCount) return new Fetched(List.copyOf(all), true);
        }
        // MAX_PAGES 까지 돌고도 totalCount 에 닿지 못했다 — 자료가 늘어난 것이니 완전하다고 보지 않는다.
        log.warn("스토리텔링을 {}쪽까지 읽고도 끝에 닿지 못했습니다({}건).", MAX_PAGES, all.size());
        return new Fetched(List.copyOf(all), false);
    }

    /** 한 쪽을 받아 온다. 재시도까지 다 실패하면 null. 호출한 쪽에서 받은 데까지만 쓰도록 한다. */
    private JsonNode requestPageOrNull(int pageNo, int numOfRows) {
        for (int attempt = 1; attempt <= MAX_PAGE_RETRIES + 1; attempt++) {
            try {
                return requestPage(pageNo, numOfRows).path("getBusanCultureStoryTelling");
            } catch (Exception e) {
                log.warn("부산 스토리텔링 API 호출에 실패했습니다({}쪽, {}/{}차): {}",
                        pageNo, attempt, MAX_PAGE_RETRIES + 1, e.getMessage());
                if (attempt <= MAX_PAGE_RETRIES) sleep(RETRY_BACKOFF_MS);
            }
        }
        return null;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
