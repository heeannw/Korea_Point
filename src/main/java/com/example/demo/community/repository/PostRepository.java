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

    /*
     * 인기 글에서만 홍보 카테고리를 뺀다. 조회수 높은 광고가 '인기' 자리를 차지하면
     * 그건 인기 글이 아니라 광고 배너다. (전체 목록에는 홍보 글도 그대로 나온다.)
     *
     * 카테고리가 없는 글도 있을 수 있어 category.id <> :x 만으로는 그런 글까지 사라진다
     * — SQL 에서 NULL <> 5 는 참이 아니다. IS NULL 을 따로 살려 둔다.
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL "
            + "AND (p.category IS NULL OR p.category.id <> :excludedCategoryId) "
            + "ORDER BY p.viewCount DESC")
    List<Post> findTrendingExcludingCategory(@Param("excludedCategoryId") Long excludedCategoryId,
                                             Pageable pageable);

    /**
     * 아직 노출 기간이 남은 홍보 글.
     *
     * <p>무작위로 뽑을 것이라 정렬은 의미가 없다. 후보만 가져와 자바에서 섞는다.
     * DB 의 random() 을 쓰면 특정 DB 에 묶이고, 후보가 수십 건 수준이라 그럴 이유가 없다.
     */
    @EntityGraph(attributePaths = {"user", "category"})
    @Query("SELECT p FROM Post p WHERE p.deletedAt IS NULL "
            + "AND p.category.id = :categoryId "
            + "AND p.promoUntil IS NOT NULL AND p.promoUntil > :now")
    List<Post> findActivePromoPosts(@Param("categoryId") Long categoryId,
                                    @Param("now") LocalDateTime now,
                                    Pageable pageable);


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
        WHERE (CAST(:categoryId AS Long) IS NULL OR c.id = :categoryId)
          AND (CAST(:authorId AS Long) IS NULL OR u.id = :authorId)
          AND (CAST(:keyword AS String) IS NULL OR :keyword = '' OR
               LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (CAST(:createdFrom AS LocalDateTime) IS NULL OR p.createdAt >= :createdFrom)
          AND (CAST(:createdTo AS LocalDateTime) IS NULL OR p.createdAt < :createdTo)
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
        WHERE (CAST(:categoryId AS Long) IS NULL OR c.id = :categoryId)
          AND (CAST(:authorId AS Long) IS NULL OR u.id = :authorId)
          AND (CAST(:keyword AS String) IS NULL OR :keyword = '' OR
               LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (CAST(:createdFrom AS LocalDateTime) IS NULL OR p.createdAt >= :createdFrom)
          AND (CAST(:createdTo AS LocalDateTime) IS NULL OR p.createdAt < :createdTo)
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

    long countByUser_IdAndCategory_NameAndDeletedAtIsNull(Long userId, String categoryName);

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
