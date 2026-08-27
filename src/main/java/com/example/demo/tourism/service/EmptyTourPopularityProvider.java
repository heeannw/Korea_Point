package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.TourPopularityDto;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 빅데이터 API 승인 전까지 쓰이는 기본 구현이다. 아무 순위도 돌려주지 않아
 * 화면에서 인기 표시가 통째로 감춰진다.
 *
 * 실제 공급자를 만들 때는 그 빈에 {@code @Primary}를 붙이면 이 구현을 대신한다.
 */
@Component
public class EmptyTourPopularityProvider implements TourPopularityProvider {

    @Override
    public Map<String, TourPopularityDto> findPopularity(String lang) {
        return Map.of();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
