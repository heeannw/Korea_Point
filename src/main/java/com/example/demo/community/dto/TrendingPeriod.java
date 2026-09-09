package com.example.demo.community.dto;

import java.time.LocalDateTime;

/**
 * 인기 글을 셀 기간.
 *
 * <p>조회수는 쌓이기만 해서, 기간을 안 나누면 오래된 글이 영원히 1위를 차지한다.
 * 그러면 "인기 글"이 아니라 "명예의 전당"이 된다.
 */
public enum TrendingPeriod {

    /** 일간. 하루 안에 올라온 글 중에서 고른다. */
    DAILY(1),
    WEEK(7),
    MONTH(30),
    /**
     * 명예의 전당. 기간을 걸지 않는 대신 정원이 있다.
     *
     * <p>다른 셋은 창을 좁혀 목록이 짧아지지만 여기는 창이 없다. 정원을 안 두면
     * 게시판 전체가 그대로 들어와 이름값을 못 한다. 정원은 CommunityProperties 에 있다.
     */
    ALL(0);

    private final int days;

    TrendingPeriod(int days) {
        this.days = days;
    }

    /** 이 시각 이후 글만 센다. 전체 기간이면 null 이라 조건이 걸리지 않는다. */
    public LocalDateTime since() {
        return days == 0 ? null : LocalDateTime.now().minusDays(days);
    }

    /** 화면에서 넘어온 값을 읽는다. 모르는 값이면 주간으로 본다. */
    public static TrendingPeriod from(String raw) {
        if (raw == null) {
            return WEEK;
        }
        String key = raw.trim().toUpperCase();
        // 일간의 옛 이름. 이 값이 든 링크가 밖에 나가 있어 계속 받아 준다.
        if ("REALTIME".equals(key)) {
            return DAILY;
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return WEEK;
        }
    }

    public String key() {
        return name().toLowerCase();
    }
}
