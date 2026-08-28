package com.example.demo.community.service;

import com.example.demo.community.dto.CategoryDto;
import com.example.demo.community.dto.CommunityDto;
import com.example.demo.community.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.community.dto.PostDto;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CategoryRepository categoryRepository;
    private final PostService postService;

    // 커뮤니티 메인 페이지 데이터 (게시글 목록 + 카테고리 목록 + 인기글)
    @Transactional(readOnly = true)
    public CommunityDto getCommunityPageData(Long categoryId, String keyword, Pageable pageable) {
        boolean hasCategory = categoryId != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        var posts = hasCategory && hasKeyword
                ? postService.searchPostsByCategory(categoryId, keyword, pageable)  // 카테고리 + 검색어 동시
                : hasCategory
                ? postService.getPostsByCategory(categoryId, pageable)              // 카테고리만
                : hasKeyword
                ? postService.searchPosts(keyword, pageable)                        // 검색어만
                : postService.getPosts(pageable);                                   // 전체

        return CommunityDto.builder()
                .posts(posts)
                .categories(getAllCategories())
                .trendingPosts(postService.getTrendingPosts())
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
                .trendingPosts(postService.getTrendingPosts())
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
