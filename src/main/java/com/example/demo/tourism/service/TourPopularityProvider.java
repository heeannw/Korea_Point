package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.TourPopularityDto;

import java.util.Map;

/**
 * 카테고리별 인기 순위를 공급한다.
 *
 * 한국관광공사 빅데이터 API(TarRlteTarService1, DataLabService 등)는 별도 활용신청이 필요해
 * 지금 인증키로는 열리지 않는다. 승인 전까지는 {@link EmptyTourPopularityProvider}가
 * 빈 결과를 돌려주고, 화면은 인기 표시를 아예 감춘다.
 * 임시 기준으로 순위를 만들어 보여주면 나중에 근거를 설명할 수 없기 때문이다.
 */
public interface TourPopularityProvider {

    /**
     * contentId를 키로 하는 인기 정보다. 순위가 없는 곳은 아예 담기지 않는다.
     * 매칭 키가 contentId가 아니라 관광지명으로 오는 API라면 이 구현체 안에서 흡수한다.
     */
    Map<String, TourPopularityDto> findPopularity(String lang);

    /** 인기 데이터를 실제로 쓸 수 있는지. 화면은 이 값이 false면 인기 표시를 숨긴다. */
    boolean isAvailable();
}
