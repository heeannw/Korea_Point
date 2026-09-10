package com.example.demo.tourism.dto;

import java.util.List;

/**
 * "나를 위한 추천" 한 건.
 *
 * @param contentId     TourAPI contentId (프론트가 PLACES 와 조인해 카드 렌더 / openDetail)
 * @param title         국문 이름 (프론트가 언어별로 다시 덮어씀)
 * @param contentTypeId 12/14/15/25/28/38
 * @param category      카테고리 슬러그
 * @param image         썸네일 (없으면 null)
 * @param score         최종 추천 점수 (0~1, 디버그/근거 표시용)
 * @param reasons       규칙 근거 태그 (["문화시설 선호", "해운대구", "미술관 자주 봄"])
 * @param genReason     Gemini 가 만든 자연스러운 한 문장 (없으면 reasons 조립 폴백)
 */
public record PersonalRecoDto(
        String contentId,
        String title,
        String contentTypeId,
        String category,
        String image,
        double score,
        List<String> reasons,
        String genReason
) {
    public PersonalRecoDto withGenReason(String s) {
        return new PersonalRecoDto(contentId, title, contentTypeId, category, image, score, reasons, s);
    }
}
