package com.example.demo.community.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 불꽃 판정의 속도 계산을 확인한다.
 *
 * <p>이 계산이 틀리면 목록의 대부분에 불꽃이 붙거나 하나도 안 붙는데, 둘 다
 * 화면을 봐야 알 수 있어서 늦게 발견된다. 여기서 수치로 잡는다.
 */
class HotPostPolicyTest {

    /** 창 길이와 완충값을 실제 기본값(7일)과 같게 두고 잰다. */
    private static final double DAMPING_DAYS = 7;

    private static double velocityOf(int views, double ageDays) {
        return HotPostPolicy.velocity(
                views, LocalDateTime.now().minusMinutes((long) (ageDays * 1440)), DAMPING_DAYS);
    }

    /**
     * 갓 올라온 글이 몇 번 읽힌 것만으로 폭주처럼 보이면 안 된다.
     *
     * <p>나이로 그냥 나누면 10분 전 글의 5회는 하루 720회가 된다. 나이에 완충값을
     * 더해 두는 이유가 이것이고, 이게 빠지면 새 글이 전부 불꽃이 된다.
     */
    @Test
    void 갓_올라온_글은_속도가_부풀지_않는다() {
        double justPosted = velocityOf(5, 10.0 / 1440);   // 10분 전 5회
        assertThat(justPosted).isLessThan(1);
    }

    /**
     * 완충값이 창 길이와 같아야 창 안에서 나이 편향이 크지 않다.
     *
     * <p>완충값을 짧게 주면 갓 올라온 글이 산술적으로 유리해져 불꽃이 맨 위에만
     * 몰리고 NEW 와 겹친다. 창 끝(7일) 글의 불이익이 두 배를 넘지 않아야 한다.
     */
    @Test
    void 창_안에서는_나이_불이익이_두_배를_넘지_않는다() {
        double newest = velocityOf(1000, 0);
        double oldestInWindow = velocityOf(1000, 7);
        assertThat(newest / oldestInWindow).isLessThanOrEqualTo(2.0);
    }

    /** 같은 조회수라면 최근 글이 더 빠르다. 불꽃은 누적이 아니라 지금이다. */
    @Test
    void 같은_조회수면_최근_글이_더_빠르다() {
        assertThat(velocityOf(500, 1)).isGreaterThan(velocityOf(500, 30));
    }

    /** 오래된 글은 조회수가 훨씬 많아도 최근에 치고 올라온 글을 못 이긴다. */
    @Test
    void 오래_쌓인_조회수는_속도를_못_이긴다() {
        double oldPopular = velocityOf(5000, 60);   // 60일에 걸쳐 5,000회
        double freshRising = velocityOf(1500, 1);   // 하루 만에 1,500회
        assertThat(freshRising).isGreaterThan(oldPopular);
    }

    /** 나이가 0이거나 미래여도 나눗셈이 깨지지 않아야 한다. */
    @Test
    void 나이가_0이어도_계산이_깨지지_않는다() {
        // 나이가 0이면 완충값만 남아 조회수/7 이 된다
        assertThat(velocityOf(700, 0)).isEqualTo(100, org.assertj.core.data.Offset.offset(1.0));
        // 시계가 어긋나 미래로 찍혀도 나이를 0으로 깔아 같은 값이 나온다
        assertThat(HotPostPolicy.velocity(700, LocalDateTime.now().plusDays(1), DAMPING_DAYS))
                .isEqualTo(100, org.assertj.core.data.Offset.offset(1.0));
    }
}
