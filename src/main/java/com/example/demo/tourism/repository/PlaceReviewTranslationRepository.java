package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.PlaceReviewTranslation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PlaceReviewTranslationRepository extends JpaRepository<PlaceReviewTranslation, Long> {

    /** 화면에 뜰 리뷰들의 번역을 한 번에 가져온다. */
    List<PlaceReviewTranslation> findByLanguageAndReviewIdIn(String language, Collection<Long> reviewIds);

    /**
     * 아직 이 언어로 안 옮긴 리뷰.
     *
     * <p>할 일 목록을 따로 두지 않고 표 자체를 목록으로 쓴다. 서버를 다시 띄워도 이어지고,
     * 새 리뷰가 올라오면 다음 차례에 저절로 잡힌다.
     */
    @Query("""
            SELECT r.id FROM PlaceReview r
             WHERE r.text IS NOT NULL
               AND NOT EXISTS (
                   SELECT 1 FROM PlaceReviewTranslation t
                    WHERE t.reviewId = r.id
                      AND t.language = :language)
             ORDER BY r.id DESC
            """)
    List<Long> findReviewIdsMissing(@Param("language") String language, Pageable pageable);

    /** 리뷰가 고쳐지거나 지워지면 옛 번역을 지운다. 보충 작업이 다시 채운다. */
    void deleteByReviewId(Long reviewId);

    boolean existsByReviewIdAndLanguage(Long reviewId, String language);
}
