package com.example.demo.tourism.dto;

import com.example.demo.tourism.entity.PlaceReview;

/** 리뷰 목록/응답 한 건. {@code mine}은 현재 사용자가 쓴 리뷰인지. */
public record PlaceReviewDto(
        Long id,
        String authorName,
        int rating,
        String text,
        String createdAt,
        boolean edited,
        boolean mine
) {
    public static PlaceReviewDto of(PlaceReview r, Long viewerUserId) {
        boolean edited = r.getCreatedAt() != null && r.getUpdatedAt() != null
                && r.getUpdatedAt().isAfter(r.getCreatedAt());
        return new PlaceReviewDto(
                r.getId(),
                r.getUser() != null ? r.getUser().getName() : null,
                r.getRating(),
                r.getText(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
                edited,
                viewerUserId != null && r.getUser() != null && viewerUserId.equals(r.getUser().getId()));
    }
}
