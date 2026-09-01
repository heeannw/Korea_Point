package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.PlaceReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {

    /** 한 장소의 리뷰 (최근순). */
    List<PlaceReview> findByContentIdOrderByCreatedAtDesc(String contentId);

    Optional<PlaceReview> findByUserIdAndContentId(Long userId, String contentId);
}
