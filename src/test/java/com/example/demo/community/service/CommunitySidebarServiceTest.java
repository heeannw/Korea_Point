package com.example.demo.community.service;

import com.example.demo.community.config.CommunityProperties;
import com.example.demo.community.dto.PostDto;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 오른쪽 레일의 가게 홍보 칸.
 *
 * <p>확인하려는 것은 하나다. <b>노출 중인 홍보 글 모두가 같은 기회를 얻는가.</b>
 * 최신순으로 뽑으면 늦게 산 사장님이 자리를 다 먹고 먼저 500P 를 낸 사람이 밀린다.
 */
class CommunitySidebarServiceTest {

    private final PostService postService = Mockito.mock(PostService.class);
    private final PostRepository postRepository = Mockito.mock(PostRepository.class);
    private final CommunityProperties properties = new CommunityProperties(5L, 7, 3, 500, 3, 7, 30, 0.9, 100, 500);

    private CommunitySidebarService service() {
        return new CommunitySidebarService(postService, postRepository, properties);
    }

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
