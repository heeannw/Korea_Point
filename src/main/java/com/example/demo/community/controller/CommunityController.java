package com.example.demo.community.controller;

import com.example.demo.community.dto.PostDto;
import com.example.demo.community.service.CommunitySidebarService;
import com.example.demo.community.service.CommentService;
import com.example.demo.community.service.CommunityService;
import com.example.demo.community.service.PostService;
import com.example.demo.user.service.UserService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;
    private final PostService postService;
    private final CommentService commentService;
    private final UserService userService;
    // 목록 오른쪽 레일(가게 홍보 · 축제). 커뮤니티 화면 전용이라 별도 서비스로 뺐다.
    private final CommunitySidebarService sidebarService;

    @GetMapping("/post-list")
    public String getPosts(@RequestParam(required = false) Long categoryId,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false, defaultValue = "false") boolean mine,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "latest") String sort,
                           Model model) {
        Sort sortOrder = "popular".equals(sort)
                ? Sort.by("viewCount").descending()
                : Sort.by("createdAt").descending();
        var pageable = PageRequest.of(page, 10, sortOrder);
        boolean myPostsOnly = mine && SecurityUtil.isLoggedIn();
        var data = myPostsOnly
                ? communityService.getMyCommunityPageData(SecurityUtil.getCurrentUserId(), categoryId, keyword, pageable)
                : communityService.getCommunityPageData(categoryId, keyword, pageable);

        int totalPages = data.getPosts().getTotalPages();
        int currentPage = data.getPosts().getNumber();
        int startPage = Math.max(0, currentPage - 4);
        int endPage = Math.min(startPage + 9, totalPages - 1);
        startPage = Math.max(0, endPage - 9);

        model.addAttribute("data", data);
        model.addAttribute("startPage", startPage);
        model.addAttribute("endPage", endPage);
        model.addAttribute("selectedCategory", categoryId);
        model.addAttribute("keyword", keyword != null ? keyword : "");
        model.addAttribute("sort", sort);
        model.addAttribute("mine", myPostsOnly);
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        model.addAttribute("communitySuspended", isCommunitySuspended());
        // 레일. 비어 있으면 화면에서 그 칸이 통째로 사라진다(축제는 TourAPI 가 죽어도 목록을 막지 않는다).
        model.addAttribute("promoPosts", sidebarService.promoPosts());
        model.addAttribute("festivals", sidebarService.festivals(org.springframework.context.i18n.LocaleContextHolder.getLocale().getLanguage()));
        return "community/post-list";
    }

    @GetMapping("/post/{id}")
    public String getPostDetail(@PathVariable Long id, Model model, HttpSession session) {
        model.addAttribute("post", postService.getPostDetail(id, session));
        model.addAttribute("comments", commentService.getCommentsByPost(id));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        model.addAttribute("currentUserId", SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserId() : null);
        model.addAttribute("communitySuspended", isCommunitySuspended());
        return "community/post-detail";
    }

    @GetMapping("/post/write")
    public String writeForm(@RequestParam(required = false) String placeContentId,
                            @RequestParam(required = false) String placeContentTypeId,
                            @RequestParam(required = false) String placeTitle,
                            Model model, RedirectAttributes redirectAttributes) {
        if (isCommunitySuspended()) {
            redirectAttributes.addFlashAttribute("communityError", "커뮤니티 기능이 정지된 계정입니다.");
            return "redirect:/community/post-list";
        }

        model.addAttribute("categories", communityService.getAllCategories());
        model.addAttribute("currentUserPoint", userService.findById(SecurityUtil.getCurrentUserId()).getPoint());
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        model.addAttribute("placeContentId", placeContentId);
        model.addAttribute("placeContentTypeId", placeContentTypeId);
        model.addAttribute("placeTitle", placeTitle);
        return "community/post-write";
    }

    @PostMapping("/post/write")
    public String createPost(@Valid @ModelAttribute PostDto.PostCreateRequest req,
                             RedirectAttributes redirectAttributes) {
        Long userId = SecurityUtil.getCurrentUserId();
        try {
            Long postId = postService.createPost(req, userId);
            return "redirect:/community/post/" + postId;
        } catch (IllegalStateException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("pointError", exception.getMessage());
            return "redirect:/community/post/write";
        }
    }

    @PostMapping("/post/{id}/delete")
    public String deletePost(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        postService.deletePost(id, userId);
        return "redirect:/community/post-list";
    }

    private boolean isCommunitySuspended() {
        return SecurityUtil.isLoggedIn() && userService.isCommunitySuspended(SecurityUtil.getCurrentUserId());
    }
}
