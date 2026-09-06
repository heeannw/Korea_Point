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

    /** 레일에 한 번에 띄우는 홍보 글 수. */
    private final int promoRailSize;

    /** 홍보 글 1건 게시 비용. 관리자가 내릴 때 환불하는 금액도 이 값이다. */
    private final int promoCost;

    public CommunityProperties(
            @Value("${community.promo-category-id:5}") long promoCategoryId,
            @Value("${community.promo-exposure-days:7}") int promoExposureDays,
            @Value("${community.promo-rail-size:3}") int promoRailSize,
            @Value("${community.promo-cost:500}") int promoCost) {
        this.promoCategoryId = promoCategoryId;
        this.promoExposureDays = promoExposureDays;
        this.promoRailSize = promoRailSize;
        this.promoCost = promoCost;
    }
}
