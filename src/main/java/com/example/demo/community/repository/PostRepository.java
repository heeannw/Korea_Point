package com.example.demo.community.repository;

import com.example.demo.community.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ?꾩껜 紐⑸줉 (??젣?섏? ?딆? 寃? ?섏씠吏?
    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByDeletedAtIsNull(Pageable pageable);

    // 移댄뀒怨좊━蹂?議고쉶 (?섏씠吏?
    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByCategoryIdAndDeletedAtIsNull(Long categoryId, Pageable pageable);

    // ?쒕ぉ 寃??(?꾩껜 移댄뀒怨좊━, ?섏씠吏?
    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByTitleContainingIgnoreCaseAndDeletedAtIsNull(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByUser_IdAndDeletedAtIsNull(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByUser_IdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(Long userId, String keyword, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByUser_IdAndCategoryIdAndDeletedAtIsNull(Long userId, Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByUser_IdAndCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(Long userId, Long categoryId, String keyword, Pageable pageable);

    // 移댄뀒怨좊━ + ?쒕ぉ 寃???숈떆 ?곸슜 (?섏씠吏?
    @EntityGraph(attributePaths = {"user", "category"})
    Page<Post> findByCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(Long categoryId, String keyword, Pageable pageable);

    // ?멸린湲 ?곸쐞 5媛?(議고쉶??湲곗?)
    @EntityGraph(attributePaths = {"user", "category"})
    List<Post> findTop5ByDeletedAtIsNullOrderByViewCountDesc();

    @EntityGraph(attributePaths = {"user", "category"})
    List<Post> findTop5ByDeletedAtIsNullOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"user", "category"})
    List<Post> findTop10ByPlaceContentIdAndDeletedAtIsNullOrderByCreatedAtDesc(String placeContentId);

    // ?④굔 議고쉶 (??젣?섏? ?딆? 寃?
    @EntityGraph(attributePaths = {"user", "category"})
    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.category WHERE p.id = :id")
    Optional<Post> findAdminPostById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "category"})
    @Query(value = """
        SELECT p FROM Post p
        JOIN p.user u
        LEFT JOIN p.category c
        WHERE (:categoryId IS NULL OR c.id = :categoryId)
          AND (:authorId IS NULL OR u.id = :authorId)
          AND (:keyword IS NULL OR :keyword = '' OR
               LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:createdFrom IS NULL OR p.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR p.createdAt < :createdTo)
          AND (
               :status = 'all' OR
               (:status = 'active' AND p.deletedAt IS NULL) OR
               (:status = 'deleted' AND p.deletedAt IS NOT NULL)
          )
          AND (:purgeReady = false OR (p.deletedAt IS NOT NULL AND p.deletedAt <= :purgeThreshold))
        """,
        countQuery = """
        SELECT COUNT(p) FROM Post p
        JOIN p.user u
        LEFT JOIN p.category c
        WHERE (:categoryId IS NULL OR c.id = :categoryId)
          AND (:authorId IS NULL OR u.id = :authorId)
          AND (:keyword IS NULL OR :keyword = '' OR
               LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:createdFrom IS NULL OR p.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR p.createdAt < :createdTo)
          AND (
               :status = 'all' OR
               (:status = 'active' AND p.deletedAt IS NULL) OR
               (:status = 'deleted' AND p.deletedAt IS NOT NULL)
          )
          AND (:purgeReady = false OR (p.deletedAt IS NOT NULL AND p.deletedAt <= :purgeThreshold))
        """)
    Page<Post> searchForAdmin(@Param("keyword") String keyword,
                              @Param("categoryId") Long categoryId,
                              @Param("authorId") Long authorId,
                              @Param("status") String status,
                              @Param("createdFrom") LocalDateTime createdFrom,
                              @Param("createdTo") LocalDateTime createdTo,
                              @Param("purgeReady") boolean purgeReady,
                              @Param("purgeThreshold") LocalDateTime purgeThreshold,
                              Pageable pageable);

    long countByDeletedAtIsNull();

    long countByUser_IdAndDeletedAtIsNull(Long userId);

    List<Post> findAllByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime deletedAt);

    // 議고쉶??+1
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // ?볤???利앷컧
    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id")
    void updateCommentCount(@Param("id") Long id, @Param("delta") int delta);
}
