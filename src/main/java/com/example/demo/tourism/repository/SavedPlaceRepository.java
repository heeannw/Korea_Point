package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.SavedPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPlaceRepository extends JpaRepository<SavedPlace, Long> {

    /** 한 사용자의 저장 목록 (최근 저장 순). */
    List<SavedPlace> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** kind(tourism/food/cafe)로 필터한 저장 목록. */
    List<SavedPlace> findByUserIdAndKindOrderByCreatedAtDesc(Long userId, String kind);

    /** 토글·중복 판별용. */
    Optional<SavedPlace> findByUserIdAndContentId(Long userId, String contentId);

    boolean existsByUserIdAndContentId(Long userId, String contentId);

    void deleteByUserIdAndContentId(Long userId, String contentId);

    long countByUserId(Long userId);
}
