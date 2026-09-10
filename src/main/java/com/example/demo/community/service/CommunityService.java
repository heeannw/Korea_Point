package com.example.demo.community.service;

import com.example.demo.community.dto.CategoryDto;
import com.example.demo.community.dto.CommunityDto;
import com.example.demo.community.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.dto.CommunityListView;
import com.example.demo.community.dto.TrendingPeriod;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CategoryRepository categoryRepository;
    private final PostService postService;

    // 커뮤니티 메인 페이지 데이터 (게시글 목록 + 카테고리 목록 + 인기글)
    @Transactional(readOnly = true)
    public CommunityDto getCommunityPageData(Long categoryId, String keyword,
                                             CommunityListView view, TrendingPeriod period,
                                             Pageable pageable) {
        boolean hasCategory = categoryId != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        // 전체글은 최신순 하나뿐이고, 조회수로 보는 일은 베스트가 맡는다.
        // 검색 중에는 기간을 걸지 않는다 — 찾는 글이 기간 밖이면 안 나와서 검색이 고장 난 것처럼 보인다.
        var posts = hasKeyword
                ? (hasCategory
                    ? postService.searchPostsByCategory(categoryId, keyword, pageable)
                    : postService.searchPosts(keyword, pageable))
                : view.isBest()
                ? postService.getBestPosts(categoryId, period, pageable)
                : hasCategory
                ? postService.getPostsByCategory(categoryId, pageable)
                : postService.getPosts(pageable);

        return CommunityDto.builder()
                .posts(posts)
                .categories(getAllCategories())
                .trendingPosts(postService.getTrendingPosts(period))
                .build();
    }

    @Transactional(readOnly = true)
    public CommunityDto getMyCommunityPageData(Long userId, Long categoryId, String keyword, Pageable pageable) {
        boolean hasCategory = categoryId != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        var posts = hasCategory && hasKeyword
                ? postService.searchPostsByUserAndCategory(userId, categoryId, keyword, pageable)
                : hasCategory
                ? postService.getPostsByUserAndCategory(userId, categoryId, pageable)
                : hasKeyword
                ? postService.searchPostsByUser(userId, keyword, pageable)
                : postService.getPostsByUser(userId, pageable);

        return CommunityDto.builder()
                .posts(posts)
                .categories(getAllCategories())
                .trendingPosts(postService.getTrendingPosts(TrendingPeriod.WEEK))
                .build();
    }

    // 카테고리 전체 목록 (정렬 순서대로)
    @Transactional(readOnly = true)
    public List<CategoryDto> getAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc()
                .stream()
                .map(c -> CategoryDto.builder()
                        .categoryId(c.getId())
                        .name(c.getName())
                        .nameEn(c.getNameEn())
                        .icon(c.getEmoji())
                        .build())
                .toList();
    }
}
