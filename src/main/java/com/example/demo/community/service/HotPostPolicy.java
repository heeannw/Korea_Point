package com.example.demo.community.service;

import com.example.demo.community.config.CommunityProperties;
import com.example.demo.community.dto.PostViewStat;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 목록에 불꽃(HOT)을 붙일지 정한다.
 *
 * <p>처음에는 "조회수 500 이상"이었다. 조회수는 시간이 지나며 쌓이기만 해서, 오래된 글이
 * 늘자 목록의 76%에 불꽃이 붙었다. 모두에게 붙는 표시는 표시가 아니다.
 *
 * <p>한국 게시판의 표준인 그누보드도 hot 조회수를 게시판마다 관리자가 정하게 해 뒀다
 * (기본값 100). 디시 개념글 커트라인이 갤러리마다 다른 것도 같은 이유다. 활동량이
 * 다르면 기준도 달라야 한다는 뜻인데, 사람이 계속 고쳐 주는 대신 최근 글을 보고
 * 스스로 잡게 했다.
 *
 * <p>기준은 누적 조회수가 아니라 <b>속도</b>다. 불꽃은 "많이 읽힌 글"이 아니라 "지금
 * 읽히고 있는 글"이라는 약속이기 때문이다. 조회수를 나이로 나누는 것은 해커뉴스가
 * 점수를 나이로 나누는 것과 같은 생각이고, 나이에 상수를 더하는 것도 거기서 가져왔다
 * (해커뉴스는 2시간을 더한다). 이 완충이 없으면 10분 전에 올라와 5번 읽힌 글이
 * 하루 720회짜리로 계산돼 갓 올라온 글이 전부 불꽃이 된다.
 *
 * <p>더하는 값은 창 길이와 같게 뒀다. 짧게 잡으면 어린 글이 산술적으로 유리해져
 * 불꽃이 전부 맨 위에 몰리는데, 그러면 NEW 를 한 번 더 붙이는 것과 다르지 않다.
 * 실제 데이터로 재 보니 반나절로 두면 다섯 개가 전부 첫 페이지에 몰렸고, 이레로
 * 두니 이레 안에 고루 퍼졌다. 창과 같게 두면 "같은 조회수면 최근 글이 더 뜨겁되,
 * 창 끝의 글이 두 배까지만 불리하다"가 된다.
 */
@Component
@RequiredArgsConstructor
public class HotPostPolicy {

    private final PostRepository postRepository;
    private final CommunityProperties communityProperties;

    /** 기준을 다시 재는 간격. 매 요청마다 세면 목록 한 번에 집계가 한 번씩 붙는다. */
    private static final Duration RECOMPUTE_EVERY = Duration.ofMinutes(5);

    /**
     * 기준을 잡으려면 표본이 이만큼은 있어야 한다.
     *
     * <p>글이 세 건뿐인데 상위 10%를 뽑으면 그중 제일 나은 하나가 불꽃이 된다.
     * 조용한 게시판에서 아무 글에나 불꽃이 붙는 것을 막는다.
     */
    private static final int MIN_SAMPLE = 10;

    /** 아직 못 쟀거나 표본이 모자라면 아무 글도 뜨겁지 않다. */
    private volatile double threshold = Double.MAX_VALUE;
    private volatile Instant computedAt = Instant.EPOCH;

    /** 지금 뜨는 글인가. */
    public boolean isHot(Post post) {
        if (post == null || post.getCreatedAt() == null) {
            return false;
        }
        // 돈을 내고 올린 글에 '지금 뜨는 글' 표시를 달아 주면 그건 표시가 아니라 광고다.
        if (post.getCategory() != null
                && post.getCategory().getId() == communityProperties.getPromoCategoryId()) {
            return false;
        }
        // 오래된 글은 아무리 많이 읽혔어도 지금 뜨는 글이 아니다.
        if (post.getCreatedAt().isBefore(windowStart())) {
            return false;
        }
        // 올라온 지 얼마 안 돼 표본이 적을 때 몇 번 읽힌 것만으로 불꽃이 붙는 것을 막는다.
        if (post.getViewCount() < communityProperties.getHotMinViews()) {
            return false;
        }
        return velocityOf(post.getViewCount(), post.getCreatedAt()) >= currentThreshold();
    }

    /**
     * 읽히는 속도. 나이에 완충값을 더해 갓 올라온 글이 폭주로 보이는 것을 막는다.
     *
     * @param dampingDays 완충값(일). 창 길이와 같게 주는 것을 전제로 한다.
     */
    static double velocity(int viewCount, LocalDateTime createdAt, double dampingDays) {
        double ageDays = Math.max(0, ChronoUnit.SECONDS.between(createdAt, LocalDateTime.now())) / 86_400.0;
        return viewCount / (ageDays + dampingDays);
    }

    private double velocityOf(int viewCount, LocalDateTime createdAt) {
        return velocity(viewCount, createdAt, communityProperties.getHotWindowDays());
    }

    private LocalDateTime windowStart() {
        return LocalDateTime.now().minusDays(communityProperties.getHotWindowDays());
    }

    private double currentThreshold() {
        if (Instant.now().isBefore(computedAt.plus(RECOMPUTE_EVERY))) {
            return threshold;
        }
        double measured = measure();
        threshold = measured;
        computedAt = Instant.now();
        return measured;
    }

    /**
     * 최근 글들의 속도에서 상위 몇 %가 되는 지점을 찾는다.
     *
     * <p>홍보 글은 표본에서 뺀다. 광고는 노출 자리를 따로 사서 조회수가 붙기 때문에
     * 같이 세면 일반 글의 기준이 밀려 올라간다.
     *
     * <p>여기서 트랜잭션을 열지 않는다. 같은 객체 안에서 부르는 호출이라
     * {@code @Transactional} 을 붙여도 프록시를 안 거쳐 아무 일도 일어나지 않는다.
     * 조회 한 번뿐이라 스프링 데이터가 여는 것으로 충분하다.
     */
    private double measure() {
        List<PostViewStat> stats = postRepository.findViewStatsSince(
                communityProperties.getPromoCategoryId(), windowStart());
        if (stats.size() < MIN_SAMPLE) {
            return Double.MAX_VALUE;
        }
        double[] velocities = stats.stream()
                .mapToDouble(s -> velocityOf(s.viewCount(), s.createdAt()))
                .sorted()
                .toArray();
        int index = (int) Math.ceil(communityProperties.getHotPercentile() * velocities.length) - 1;
        return velocities[Math.min(Math.max(index, 0), velocities.length - 1)];
    }
}
