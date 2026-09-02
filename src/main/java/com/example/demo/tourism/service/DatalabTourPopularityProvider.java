package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.TourPopularityDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 관광 데이터랩 '세대별 인기관광지'(부산) 스냅샷을 인기 순위로 공급한다.
 *
 * <p>표는 빌드 타임 스크립트({@code scripts/tourism/build_popular_attractions.py})가
 * 만든 {@code tourdata/tourism/popular_attractions_busan.json} 이다.
 * 데이터랩 관심지점 ID ↔ contentId 는 관광지명 매칭이라, 매칭된 것만 담긴다(현재 ~34곳).
 * 연 1회 정도 CSV 재다운로드 → 스크립트 재실행으로 갱신한다.</p>
 *
 * <p>{@code @Primary} 라 {@link EmptyTourPopularityProvider} 대신 이 빈이 쓰인다.
 * JSON 이 없거나 비면 {@link #isAvailable()} 이 false 라 화면에서 인기 표시가 숨겨진다.</p>
 */
@Component
@Primary
public class DatalabTourPopularityProvider implements TourPopularityProvider {

    private static final Logger log = LoggerFactory.getLogger(DatalabTourPopularityProvider.class);
    private static final String RESOURCE = "tourdata/tourism/popular_attractions_busan.json";

    /** JSON 한 줄. contentId -> 이 레코드. */
    private record Entry(String name, Integer rank, Double ratio, String gubun, Map<String, Double> byAge) { }

    private final ObjectMapper objectMapper;
    private Map<String, TourPopularityDto> byContentId = Map.of();

    public DatalabTourPopularityProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 인기 관광지 표시가 숨겨진다. "
                        + "scripts/tourism/build_popular_attractions.py 를 실행해 생성하라.", RESOURCE);
                return;
            }
            Map<String, Entry> raw = objectMapper.readValue(in, new TypeReference<Map<String, Entry>>() { });
            Map<String, TourPopularityDto> loaded = new HashMap<>();
            raw.forEach((contentId, e) -> {
                TourPopularityDto dto = new TourPopularityDto(e.rank(), e.gubun(), e.ratio());
                dto.setRatioByAge(e.byAge());
                loaded.put(contentId, dto);
            });
            byContentId = loaded;
            log.info("인기 관광지(데이터랩) 로드: {}곳", byContentId.size());
        } catch (Exception ex) {
            log.warn("{} 을 읽지 못했다 — 인기 표시가 숨겨진다.", RESOURCE, ex);
        }
    }

    @Override
    public Map<String, TourPopularityDto> findPopularity(String lang) {
        return byContentId;   // 언어 무관 (contentId 기준)
    }

    @Override
    public boolean isAvailable() {
        return !byContentId.isEmpty();
    }
}
