package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.PlaceReviewDto;
import com.example.demo.tourism.service.PlaceReviewService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 장소 방문 리뷰 API. {@code /tour/**} 이라 SecurityConfig에서 이미 인증 필수.
 * 목록은 비로그인도 볼 수 있게 열어 두되(현재 /tour/** 전체가 인증이라 실질 로그인 필요),
 * 작성·삭제는 로그인 사용자만.
 */
@RestController
@RequestMapping("/tour/api/reviews")
@RequiredArgsConstructor
public class PlaceReviewController {

    private final PlaceReviewService reviewService;

    @GetMapping("/{contentId}")
    public List<PlaceReviewDto> list(@PathVariable String contentId) {
        Long viewer = SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserId() : null;
        return reviewService.list(contentId, viewer);
    }

    /** body: { contentId, contentTypeId, rating(1~5), text } */
    @PostMapping
    public PlaceReviewDto save(@RequestBody Map<String, Object> body) {
        return reviewService.save(
                SecurityUtil.getCurrentUserId(),
                String.valueOf(body.get("contentId")),
                body.get("contentTypeId") == null ? null : String.valueOf(body.get("contentTypeId")),
                body.get("rating") == null ? 0 : Integer.parseInt(String.valueOf(body.get("rating"))),
                body.get("text") == null ? null : String.valueOf(body.get("text")));
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId) {
        reviewService.delete(SecurityUtil.getCurrentUserId(), reviewId);
        return ResponseEntity.noContent().build();
    }
}
