package com.example.demo.community.controller;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tour/api/community")
@RequiredArgsConstructor
public class CommunityPlaceApiController {
    private final PostService postService;

    @GetMapping("/{contentId}")
    public List<PostDto> postsForPlace(@PathVariable String contentId) {
        return postService.getPostsByPlace(contentId);
    }
}
