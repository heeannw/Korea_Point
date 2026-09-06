package com.example.demo.community.service;

import com.example.demo.community.config.CommunityProperties;
import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.dto.TourPlaceDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오른쪽 레일의 축제 칸.
 *
 * <p>확인하려는 것은 하나다. <b>TourAPI 가 어떤 상태든 커뮤니티 요청은 기다리지 않는다.</b>
 * 홈이 TourAPI 를 동기로 부르는 바람에 API 가 멈추자 첫 화면이 통째로 안 열렸다.
 * 커뮤니티에서 같은 일이 반복되면 안 된다.
 */
class CommunitySidebarServiceTest {

    private final TourApiClient tourApiClient = Mockito.mock(TourApiClient.class);
    private final PostService postService = Mockito.mock(PostService.class);
    private final PostRepository postRepository = Mockito.mock(PostRepository.class);
    private final CommunityProperties properties = new CommunityProperties(5L, 7, 3);

    private CommunitySidebarService service() {
        return new CommunitySidebarService(postService, postRepository, tourApiClient, properties);
    }

    private static TourPlaceDto festival(String title) {
        TourPlaceDto dto = new TourPlaceDto();
        dto.setTitle(title);
        return dto;
    }

    /** 조건이 참이 될 때까지 기다린다. 갱신이 뒤에서 돌기 때문에 필요하다. */
    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("3초 안에 조건이 만족되지 않았습니다");
    }

    @Test
    void 축제를_받아오면_다음_요청부터_보인다() {
        Mockito.when(tourApiClient.findCurrentBusanFestivals("ko"))
                .thenReturn(List.of(festival("부산불꽃축제"), festival("광안리 드론쇼")));
        CommunitySidebarService service = service();

        // 첫 요청은 캐시가 비어 있으니 빈 목록이다. 화면은 이때 축제 칸을 감춘다.
        assertThat(service.festivals("ko")).isEmpty();

        await(() -> !service.festivals("ko").isEmpty());
        assertThat(service.festivals("ko"))
                .extracting(TourPlaceDto::getTitle)
                .containsExactly("부산불꽃축제", "광안리 드론쇼");
    }

    @Test
    void API가_죽어도_예외를_던지지_않는다() {
        // 지금 실제로 벌어지고 있는 상황이다. 이때 커뮤니티까지 같이 죽으면 안 된다.
        Mockito.when(tourApiClient.findCurrentBusanFestivals(Mockito.anyString()))
                .thenThrow(new RuntimeException("TourAPI 호출 실패"));
        CommunitySidebarService service = service();

        assertThat(service.festivals("ko")).isEmpty();
        assertThat(service.festivals("ko")).isEmpty();
    }

    @Test
    void 세_개까지만_싣는다() {
        Mockito.when(tourApiClient.findCurrentBusanFestivals("ko"))
                .thenReturn(List.of(festival("1"), festival("2"), festival("3"),
                        festival("4"), festival("5")));
        CommunitySidebarService service = service();

        service.festivals("ko");
        await(() -> !service.festivals("ko").isEmpty());
        assertThat(service.festivals("ko")).hasSize(3);
    }

    @Test
    void 한_번_받아_두면_이후_실패해도_계속_보여_준다() {
        Mockito.when(tourApiClient.findCurrentBusanFestivals("ko"))
                .thenReturn(List.of(festival("부산불꽃축제")))
                .thenThrow(new RuntimeException("TourAPI 호출 실패"));
        CommunitySidebarService service = service();

        service.festivals("ko");
        await(() -> !service.festivals("ko").isEmpty());

        // 갱신에 실패해도 앞서 받아 둔 것은 지우지 않는다. 빈 칸보다 낫다.
        assertThat(service.festivals("ko"))
                .extracting(TourPlaceDto::getTitle)
                .containsExactly("부산불꽃축제");
    }

    // ── 가게 홍보 레일 ───────────────────────────────────────

    private static Post promo(long id) {
        Post p = new Post();
        p.setId(id);
        p.setTitle("가게 " + id);
        return p;
    }

    /** 레일은 후보를 리포지토리에서 받아 자바에서 섞는다. 변환만 흉내 낸다. */
    private void givenActivePromos(List<Post> posts) {
        Mockito.when(postRepository.findActivePromoPosts(
                ArgumentMatchers.eq(5L), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(posts);
        Mockito.when(postService.toListDto(ArgumentMatchers.any(Post.class)))
                .thenAnswer(inv -> PostDto.builder()
                        .postId(((Post) inv.getArgument(0)).getId())
                        .title(((Post) inv.getArgument(0)).getTitle())
                        .build());
    }

    @Test
    void 레일은_설정한_자리_수만큼만_뽑는다() {
        givenActivePromos(IntStream.rangeClosed(1, 10).mapToObj(i -> promo(i)).toList());

        assertThat(service().promoPosts()).hasSize(3);   // properties 의 promoRailSize
    }

    @Test
    void 홍보글이_자리보다_적으면_있는_만큼만_나온다() {
        givenActivePromos(List.of(promo(1), promo(2)));

        assertThat(service().promoPosts()).hasSize(2);
    }

    @Test
    void 노출중인_홍보글이_없으면_빈_목록이다() {
        // 화면은 이때 '우리 가게 홍보' 칸을 통째로 감춘다
        givenActivePromos(List.of());

        assertThat(service().promoPosts()).isEmpty();
    }

    @Test
    void 매번_같은_글만_나오지_않는다() {
        // 최신순으로 뽑으면 늦게 산 사장님이 자리를 다 먹는다. 무작위여야 모두에게 기회가 간다.
        givenActivePromos(IntStream.rangeClosed(1, 12).mapToObj(i -> promo(i)).toList());

        CommunitySidebarService service = service();
        Set<Long> seen = IntStream.range(0, 30)
                .boxed()
                .flatMap(i -> service.promoPosts().stream())
                .map(PostDto::getPostId)
                .collect(Collectors.toSet());

        // 30번 뽑았는데 3개만 나왔다면 무작위가 아니라 고정된 것이다
        assertThat(seen).hasSizeGreaterThan(3);
    }
}
