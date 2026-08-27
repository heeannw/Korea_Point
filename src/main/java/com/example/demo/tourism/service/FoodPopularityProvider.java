package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.FoodPopularityDto;

import java.util.Map;

/** 음식점·카페 TOP N을 공급하는 교체 가능한 경계다. */
public interface FoodPopularityProvider {

    String FOOD_SECTION = "food";
    String CAFE_SECTION = "cafe";

    /** contentId를 키로 하며, 순위가 없는 장소는 결과에 포함하지 않는다. */
    Map<String, FoodPopularityDto> findPopularity(String section, String lang, int limit);

    /** 실제 예약·행동·빅데이터 공급자가 연결되었는지 여부다. */
    boolean isAvailable();
}
