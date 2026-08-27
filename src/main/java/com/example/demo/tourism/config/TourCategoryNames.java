package com.example.demo.tourism.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;

/**
 * TourAPI 분류체계(lclsSystm) 코드를 사람이 읽는 이름으로 옮긴다.
 *
 * 기존 cat1/2/3은 부산 기준 17%만 채워져 있어 쓸 수 없고,
 * lclsSystm1/2/3은 100% 채워져 있어 이쪽을 쓴다.
 * 표는 lclsSystmCode2 조회 결과를 그대로 받아 리소스로 두었다.
 */
@Component
public class TourCategoryNames {

    private static final Logger log = LoggerFactory.getLogger(TourCategoryNames.class);
    private static final String RESOURCE = "tourdata/tourism/lcls_systm.json";

    private final ObjectMapper objectMapper;
    private Map<String, String> namesByCode = Map.of();

    public TourCategoryNames(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                log.warn("{} 없음 — 관광 타입명 대신 콘텐츠 유형명으로 표시된다.", RESOURCE);
                return;
            }
            namesByCode = objectMapper.readValue(in, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            log.warn("{} 을 읽지 못했다 — 관광 타입명 표시를 건너뛴다.", RESOURCE, e);
        }
    }

    /**
     * 소분류 이름을 우선 쓰고, 없으면 중분류·대분류로 물러난다.
     * 셋 다 못 찾으면 null을 돌려 호출한 쪽이 기존 유형명을 쓰게 한다.
     */
    public String resolve(String small, String medium, String large) {
        String name = lookup(small);
        if (name == null) name = lookup(medium);
        if (name == null) name = lookup(large);
        return name;
    }

    private String lookup(String code) {
        if (code == null || code.isBlank()) return null;
        String name = namesByCode.get(code.trim());
        return name == null || name.isBlank() ? null : name;
    }

    public int size() {
        return namesByCode.size();
    }
}
