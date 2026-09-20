package com.example.demo.community.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 화면 정책값.
 */
@Getter
@Component
public class CommunityProperties {

    /**
     * 홍보 카테고리 id.
     *
     * <p>이 카테고리 글은 인기 글에서만 뺀다. 조회수 높은 광고가 '인기' 자리를 차지하면
     * 그건 인기 글이 아니라 광고 배너다. 전체 목록에는 그대로 나온다 —
     * 500P 를 내고 올린 글이라 목록에서 빼면 산 것이 사라진다.
     */
    private final long promoCategoryId;

    /** 홍보 글을 오른쪽 레일에 띄워 주는 기간(일). 500P 로 사는 것이 이 기간이다. */
    private final int promoExposureDays;

    /**
     * 레일에 한 번에 띄우는 홍보 글 수.
     *
     * <p>넷이면 레일이 목록 한 페이지와 거의 같은 높이가 된다(재 보니 730px 대 732px).
     * 셋이면 170px 이 남아 오른쪽이 비어 보이고, 다섯이면 그만큼 넘친다.
     * 카드 높이나 한 페이지 글 수를 바꾸면 이 값도 다시 재야 한다.
     */
    private final int promoRailSize;

    /** 홍보 글 1건 게시 비용. 관리자가 내릴 때 환불하는 금액도 이 값이다. */
    private final int promoCost;

    /**
     * NEW 를 붙여 두는 시간.
     *
     * <p>그누보드 기본값은 24시간이지만 그건 게시판마다 정하라고 열어 둔 값이다.
     * 우리는 하루 열세 건쯤 올라오고 한 페이지가 열 건이라, 24시간으로 두면
     * 첫 페이지 열 건이 통째로 NEW 가 된다. 재 보니 세 시간이 세 건으로,
     * 첫 페이지의 3할쯤에만 붙는다.
     *
     * <p>글이 올라오는 속도가 바뀌면 이 값도 다시 재야 한다. 그래서 상수가 아니라
     * 설정값이다.
     */
    private final int freshHours;

    /**
     * 불꽃 후보로 볼 기간(일). 이보다 오래된 글은 아무리 읽혔어도 '지금'이 아니다.
     *
     * <p>속도를 낼 때 나이에 더하는 완충값으로도 같이 쓴다. 둘을 맞춰야 창 안에서
     * 어느 나이대도 유리해지지 않는다.
     */
    private final int hotWindowDays;

    /** 불꽃이 붙으려면 최소한 이만큼은 읽혀야 한다. */
    private final int hotMinViews;

    /** 최근 글 속도 중 상위 몇 %를 불꽃으로 볼지. 0.9 면 상위 10%. */
    private final double hotPercentile;

    /**
     * 명예의 전당 정원.
     *
     * <p>일간·주간·월간은 기간이 좁아 목록이 저절로 짧지만, 전체 기간에는 창이 없어
     * 게시판 전체가 그대로 들어온다. 그러면 끝까지 넘겼을 때 조회수 한 자릿수 글까지
     * 나와서, 이름은 전당인데 전원이 입장한다.
     *
     * <p>오늘의유머 베오베가 추천 10 → 70 으로 관문을 겹쳐 두는 것처럼, 전당은
     * 기간을 넓힌 자리가 아니라 문턱이 높은 자리여야 한다. 우리는 추천 기능이 없어
     * 조회수 순 상위 몇 건까지만이라는 정원으로 그 문턱을 대신한다.
     */
    private final int hallOfFameSize;

    /**
     * 베스트에 오르는 최소 조회수.
     *
     * <p>문턱이 없으면 글이 적은 기간에는 조회수 두어 번인 글이 그대로 베스트에 오른다.
     * 방금 올린 글이 베스트 첫 줄에 뜨면 목록 자체를 못 믿게 된다.
     */
    private final int bestMinViews;

    public CommunityProperties(
            @Value("${community.promo-category-id:5}") long promoCategoryId,
            @Value("${community.promo-exposure-days:7}") int promoExposureDays,
            @Value("${community.promo-rail-size:4}") int promoRailSize,
            @Value("${community.promo-cost:500}") int promoCost,
            @Value("${community.fresh-hours:3}") int freshHours,
            @Value("${community.hot-window-days:7}") int hotWindowDays,
            @Value("${community.hot-min-views:30}") int hotMinViews,
            @Value("${community.hot-percentile:0.9}") double hotPercentile,
            @Value("${community.hall-of-fame-size:100}") int hallOfFameSize,
            @Value("${community.best-min-views:500}") int bestMinViews) {
        this.promoCategoryId = promoCategoryId;
        this.promoExposureDays = promoExposureDays;
        this.promoRailSize = promoRailSize;
        this.promoCost = promoCost;
        this.freshHours = freshHours;
        this.hotWindowDays = hotWindowDays;
        this.hotMinViews = hotMinViews;
        this.hotPercentile = hotPercentile;
        this.hallOfFameSize = hallOfFameSize;
        this.bestMinViews = bestMinViews;
    }
}
