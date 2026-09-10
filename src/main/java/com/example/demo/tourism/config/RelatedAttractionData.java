package com.example.demo.tourism.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 관광지별 "연관 관광지"(다른 여행자들의 실제 방문 동선 기반).
 *
 * <p>표는 빌드 타임 스크립트({@code scripts/tourism/build_related_attractions.py})가 만든
 * {@code tourdata/tourism/related_attractions_busan.json} — 한국관광공사 TarRlteTarService1(부산 16개 구)를
 * 관광지명으로 contentId 매칭한 것. 연관은 '관광지' 대분류만(음식·숙박 제외). 기준 151곳.
 * 갱신은 스크립트 재실행(월 단위 데이터).</p>
 */
@Component
public class RelatedAttractionData {

    private static final Logger log = LoggerFactory.getLogger(RelatedAttractionData.class);
    private static final String RESOURCE = "tourdata/tourism/related_attractions_busan.json";

    /** JSON 한 줄. */
    public record Related(String contentId, int rank) { }

    private final ObjectMapper objectMapper;
    private Map<String, List<Related>> byContentId = Map.of();

    public RelatedAttractionData(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 연관 관광지 섹션이 항상 비어 보인다. "
                        + "scripts/tourism/build_related_attractions.py 를 실행해 생성하라.", RESOURCE);
                return;
            }
            byContentId = objectMapper.readValue(in, new TypeReference<Map<String, List<Related>>>() { });
            log.info("연관 관광지 로드: 기준 {}곳", byContentId.size());
        } catch (Exception e) {
            log.warn("{} 을 읽지 못했다.", RESOURCE, e);
        }
    }

    /** rank 오름차순. 없으면 빈 리스트. */
    public List<Related> forContent(String contentId) {
        return byContentId.getOrDefault(contentId, List.of());
    }
}
