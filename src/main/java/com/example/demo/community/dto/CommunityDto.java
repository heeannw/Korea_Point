package com.example.demo.community.dto;

import lombok.*;
import org.springframework.data.domain.Page;

import java.util.List;

// 커뮤니티 메인 페이지에 필요한 데이터 묶음
@Getter
@Builder
public class CommunityDto {

    private Page<PostDto> posts;
    private List<CategoryDto> categories;
    private List<PostDto> trendingPosts;
}
