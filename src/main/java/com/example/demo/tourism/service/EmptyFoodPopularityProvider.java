package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.FoodPopularityDto;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 인기 데이터 연결 전 기본 구현. 가짜 순위를 만들지 않고 항상 빈 결과를 반환한다. */
@Component
public class EmptyFoodPopularityProvider implements FoodPopularityProvider {

    @Override
    public Map<String, FoodPopularityDto> findPopularity(String section, String lang, int limit) {
        return Map.of();
    }

    @Override
    public boolean isAvailable() {
        return false;
    }
}
