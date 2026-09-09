package com.example.demo.home.service;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.dto.TrendingPeriod;
import com.example.demo.community.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final PostService postService;

    /** 홈에 띄우는 커뮤니티 인기 글. 첫 화면이라 '이번 주'가 맞다. */
    public List<PostDto> getTrendingPosts() {
        return postService.getTrendingPosts(TrendingPeriod.WEEK);
    }
}
