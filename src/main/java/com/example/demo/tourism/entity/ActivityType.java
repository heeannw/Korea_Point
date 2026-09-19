package com.example.demo.tourism.entity;

/** 개인화 추천에 쓰는 사용자 행동 종류. 가중치는 PersonalRecommendService 에서 준다. */
public enum ActivityType {
    /** 상세 패널을 열었다. */
    DETAIL_VIEW,
    /** 장소를 찜(저장)했다. */
    SAVE,
    /** 검색어로 조회했다. */
    SEARCH,
    /** 카테고리 칩을 골랐다. */
    CATEGORY,
    /** 리뷰를 남겼다. */
    REVIEW
}
