package com.example.demo.community.controller;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.service.CommunityService;
import com.example.demo.community.service.CommentService;
import com.example.demo.community.service.PostService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final PostService postService;
    private final CommentService commentService;

    // 게시글 목록 (카테고리 필터, 검색어 지원)
    @GetMapping("/post-list")
    public String getPosts(@RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String keyword,
                           Model model) {
        model.addAttribute("data", communityService.getCommunityPageData(categoryId, keyword));
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        return "community/post-list";
    }

    // 게시글 상세
    @GetMapping("/post/{id}")
    public String getPostDetail(@PathVariable Long id, Model model) {
        model.addAttribute("post", postService.getPostDetail(id));
        model.addAttribute("comments", commentService.getCommentsByPost(id));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        return "community/post-detail";
    }

    // 게시글 작성 폼
    @GetMapping("/post/write")
    public String writeForm(Model model) {
        model.addAttribute("categories", communityService.getAllCategories());
        return "community/post-write";
    }

    // 게시글 저장
    @PostMapping("/post/write")
    public String createPost(@Valid @ModelAttribute PostDto.PostCreateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long postId = postService.createPost(req, userId);
        return "redirect:/community/post/" + postId;
    }

    // 게시글 삭제
    @PostMapping("/post/{id}/delete")
    public String deletePost(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        postService.deletePost(id, userId);
        return "redirect:/community/post-list";
    }
}
