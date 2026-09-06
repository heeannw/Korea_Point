package com.example.demo.community.service;

import com.example.demo.community.config.CommunityProperties;
import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.dto.TourPlaceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 커뮤니티 목록 오른쪽 레일에 들어갈 것들.
 *
 * <p>가게 홍보 글과 부산 축제를 담는다. 어느 쪽이든 비면 화면에서 그 칸이 통째로 사라진다.
 *
 * <p>축제는 TourAPI 에서 오지만 <b>요청 스레드는 절대 기다리지 않는다.</b>
 * 지금 캐시에 있는 것만 즉시 돌려주고 갱신은 뒤에서 돈다. 커뮤니티는 외부 API 가 죽어도
 * 멀쩡해야 하는 화면이다 — 홈이 TourAPI 를 요청마다 동기로 부르는 바람에
 * API 가 멈추자 첫 화면이 통째로 안 열리는 일을 이미 겪었다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunitySidebarService {

    private final PostService postService;
    private final PostRepository postRepository;
    private final TourApiClient tourApiClient;
    private final CommunityProperties communityProperties;

    /** 무작위로 뽑기 전에 가져올 후보 수. 이보다 많아지면 그때 다른 방식을 고민한다. */
    private static final int CANDIDATE_LIMIT = 100;
    private static final int FESTIVAL_LIMIT = 3;

    /** 성공하면 30분, 실패하면 5분 뒤 재시도. 죽은 API 를 매 요청 두드리지 않는다. */
    private static final long OK_TTL_MS = 30 * 60_000;
    private static final long FAIL_TTL_MS = 5 * 60_000;

    private final Map<String, List<TourPlaceDto>> festivalCache = new ConcurrentHashMap<>();
    private final Map<String, Long> nextRefreshAt = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 레일에 띄울 가게 홍보 글.
     *
     * <p>노출 기간이 남은 홍보 글 중에서 <b>무작위로</b> 뽑는다.
     * 최신순으로 뽑으면 늦게 산 사장님이 자리를 다 먹고, 먼저 500P 를 낸 사람이
     * 새 글이 올라오는 순간 밀린다. 무작위면 노출 중인 모두가 같은 기대 노출을 얻는다.
     */
    @Transactional(readOnly = true)
    public List<PostDto> promoPosts() {
        List<Post> candidates = new ArrayList<>(postRepository.findActivePromoPosts(
                communityProperties.getPromoCategoryId(),
                LocalDateTime.now(),
                PageRequest.of(0, CANDIDATE_LIMIT)));

        Collections.shuffle(candidates);
        return candidates.stream()
                .limit(communityProperties.getPromoRailSize())
                .map(postService::toListDto)
                .toList();
    }

    /**
     * 진행 중인 부산 축제.
     *
     * <p>캐시에 있는 것만 바로 돌려준다. 비어 있으면 빈 목록이고 화면은 그 칸을 감춘다.
     * 서버를 막 켠 직후엔 비어 보이지만, 그 대가로 목록이 절대 느려지지 않는다.
     */
    public List<TourPlaceDto> festivals(String lang) {
        String key = (lang == null || lang.isBlank()) ? "ko" : lang;
        refreshInBackground(key);
        return festivalCache.getOrDefault(key, List.of());
    }

    private void refreshInBackground(String lang) {
        if (System.currentTimeMillis() < nextRefreshAt.getOrDefault(lang, 0L)) {
            return;
        }
        // 요청이 몰려도 갱신은 한 번만 돈다.
        if (!refreshing.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                List<TourPlaceDto> found = tourApiClient.findCurrentBusanFestivals(lang).stream()
                        .limit(FESTIVAL_LIMIT)
                        .toList();
                festivalCache.put(lang, found);
                nextRefreshAt.put(lang, System.currentTimeMillis() + OK_TTL_MS);
                log.info("[커뮤니티] 축제 {}건 갱신 ({})", found.size(), lang);
            } catch (Exception e) {
                // 실패해도 이전 캐시는 남긴다. 한 번 받아 둔 게 있으면 계속 보여 주는 편이 낫다.
                nextRefreshAt.put(lang, System.currentTimeMillis() + FAIL_TTL_MS);
                log.warn("[커뮤니티] 축제 갱신 실패 — 이번엔 건너뛴다: {}", e.getMessage());
            } finally {
                refreshing.set(false);
            }
        });
    }
}
