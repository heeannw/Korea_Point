package com.example.demo.community.dto;

/**
 * 커뮤니티 목록을 어떤 방식으로 볼지.
 *
 * <p>베스트를 별도 화면으로 두지 않고 목록 안에서 갈아 끼운다. 게시판이 하나뿐인
 * 구조에서 베스트만 다른 페이지로 빼면 탭·검색·글쓰기가 전부 사라져 커뮤니티를
 * 떠난 것처럼 보인다.
 *
 * <p>정렬 축은 두지 않는다. 목록에 정렬을 주는 커뮤니티들은 기준이 여럿이고
 * 기간까지 곱해져 있는데(루리웹·레딧·레미·쿨엔조이), 우리는 추천 기능이 없어
 * 축이 조회수 하나뿐이다. 그 하나는 베스트가 이미 하는 일이라, 정렬로 두면
 * 광고가 섞이고 순위도 없는 열등한 베스트가 하나 더 생긴다. 디시·뽐뿌·더쿠가
 * 인기 글을 정렬이 아니라 별도 화면으로 빼 둔 것과 같은 판단이다.
 */
public enum CommunityListView {

    /** 최신순. 게시판의 기본이자 전체글의 유일한 순서다. */
    LATEST,
    /** 베스트. 기간 안에서 조회수 순이며 순위를 매겨 보여 준다. */
    BEST;

    public static CommunityListView from(String view) {
        if ("best".equalsIgnoreCase(view)) {
            return BEST;
        }
        return LATEST;
    }

    public String key() {
        return name().toLowerCase();
    }

    public boolean isBest() {
        return this == BEST;
    }
}
