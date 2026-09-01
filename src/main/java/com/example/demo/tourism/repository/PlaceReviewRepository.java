package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.PlaceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlaceReviewRepository extends JpaRepository<PlaceReview, Long> {

    /** 한 장소의 리뷰 (최근순). */
    List<PlaceReview> findByContentIdOrderByCreatedAtDesc(String contentId);

    Optional<PlaceReview> findByUserIdAndContentId(Long userId, String contentId);

    /** 장소별 평균 별점·개수. row = [contentId(String), avg(Double), count(Long)]. */
    @Query("SELECT r.contentId, AVG(r.rating), COUNT(r) FROM PlaceReview r GROUP BY r.contentId")
    List<Object[]> aggregateRatingByContent();
}
