package com.example.demo.home.service;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final PostService postService;

    public List<PostDto> getTrendingPosts() {
        return postService.getTrendingPosts();
    }
}
