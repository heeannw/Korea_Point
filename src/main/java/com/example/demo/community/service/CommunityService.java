package com.example.demo.community.service;

import com.example.demo.community.dto.CategoryDto;
import com.example.demo.community.dto.CommunityDto;
import com.example.demo.community.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
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
    public CommunityDto getCommunityPageData(Long categoryId, String keyword) {
        boolean hasCategory = categoryId != null;
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        List<PostDto> posts;
        if (hasCategory && hasKeyword) {
            posts = postService.searchPostsByCategory(categoryId, keyword); // 카테고리 + 검색어 동시
        } else if (hasCategory) {
            posts = postService.getPostsByCategory(categoryId);             // 카테고리만
        } else if (hasKeyword) {
            posts = postService.searchPosts(keyword);                       // 검색어만
        } else {
            posts = postService.getPosts();                                 // 전체
        }

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
                        .icon(c.getEmoji())
                        .build())
                .toList();
    }
}
