package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.PlaceReviewDto;
import com.example.demo.tourism.entity.PlaceReview;
import com.example.demo.tourism.repository.PlaceReviewRepository;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 장소 방문 리뷰 CRUD. 한 사용자는 한 장소에 리뷰 하나(있으면 덮어쓴다). */
@Service
@RequiredArgsConstructor
public class PlaceReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<PlaceReviewDto> list(String contentId, Long viewerUserId) {
        return reviewRepository.findByContentIdOrderByCreatedAtDesc(contentId).stream()
                .map(r -> PlaceReviewDto.of(r, viewerUserId))
                .toList();
    }

    /** 별점(1~5)과 텍스트를 저장한다. 기존 리뷰가 있으면 수정. */
    @Transactional
    public PlaceReviewDto save(Long userId, String contentId, String contentTypeId, int rating, String text) {
        int clamped = Math.max(1, Math.min(5, rating));
        PlaceReview review = reviewRepository.findByUserIdAndContentId(userId, contentId)
                .orElseGet(() -> PlaceReview.builder()
                        .user(userRepository.getReferenceById(userId))
                        .contentId(contentId)
                        .contentTypeId(contentTypeId)
                        .build());
        review.setRating(clamped);
        review.setText(text == null || text.isBlank() ? null : text.trim());
        return PlaceReviewDto.of(reviewRepository.save(review), userId);
    }

    /** 자기 리뷰만 삭제. */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        reviewRepository.findById(reviewId)
                .filter(r -> r.getUser() != null && userId.equals(r.getUser().getId()))
                .ifPresent(reviewRepository::delete);
    }
}
