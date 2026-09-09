package com.example.demo.tourism.dto;

import java.util.Map;
import java.util.Set;

/**
 * 최근 행동 로그(+ 콜드스타트 선호)로 계산한 사용자 취향.
 * 값은 0~1 로 정규화된 가중치.
 *
 * @param typeWeight   카테고리 슬러그(attraction/culture/leisure/shopping/festival) → 선호도
 * @param areaWeight   구 이름 → 선호도
 * @param detailWeight 소분류명(해수욕장·미술관·사찰 …) → 선호도
 * @param seen         이미 본/찜한 contentId (감점 대상)
 * @param recentArea   가장 최근 행동의 구 (recency 가산용, 없으면 null)
 * @param recentDetail 가장 최근 행동의 소분류 (없으면 null)
 * @param coldStart    행동 로그가 없어 선호 카테고리만으로 만든 프로필인지
 */
public record UserTasteProfile(
        Map<String, Double> typeWeight,
        Map<String, Double> areaWeight,
        Map<String, Double> detailWeight,
        Set<String> seen,
        String recentArea,
        String recentDetail,
        boolean coldStart
) {
    public boolean isEmpty() {
        return typeWeight.isEmpty() && areaWeight.isEmpty() && detailWeight.isEmpty();
    }
}
