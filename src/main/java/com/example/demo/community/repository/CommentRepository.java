package com.example.demo.community.repository;

import com.example.demo.community.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 寃뚯떆湲??理쒖긽???볤?留?議고쉶 (parent媛 null = ??볤? ?꾨땶 寃? ?앹꽦???ㅻ쫫李⑥닚)
    // @EntityGraph濡?children????踰덉뿉 JOIN 濡쒕뵫 ??N+1 荑쇰━ 諛⑹?
    @EntityGraph(attributePaths = {"children", "user", "children.user"})
    List<Comment> findByPostIdAndParentIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(Long postId);

    @EntityGraph(attributePaths = {"user", "post", "parent"})
    @Query(value = """
        SELECT c FROM Comment c
        JOIN c.user u
        JOIN c.post p
        WHERE (:postId IS NULL OR p.id = :postId)
          AND (:authorId IS NULL OR u.id = :authorId)
          AND (:keyword IS NULL OR :keyword = '' OR
               LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:createdFrom IS NULL OR c.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR c.createdAt < :createdTo)
          AND (
               :status = 'all' OR
               (:status = 'active' AND c.deletedAt IS NULL) OR
               (:status = 'deleted' AND c.deletedAt IS NOT NULL)
          )
          AND (:purgeReady = false OR (c.deletedAt IS NOT NULL AND c.deletedAt <= :purgeThreshold))
        """,
        countQuery = """
        SELECT COUNT(c) FROM Comment c
        JOIN c.user u
        JOIN c.post p
        WHERE (:postId IS NULL OR p.id = :postId)
          AND (:authorId IS NULL OR u.id = :authorId)
          AND (:keyword IS NULL OR :keyword = '' OR
               LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:createdFrom IS NULL OR c.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR c.createdAt < :createdTo)
          AND (
               :status = 'all' OR
               (:status = 'active' AND c.deletedAt IS NULL) OR
               (:status = 'deleted' AND c.deletedAt IS NOT NULL)
          )
          AND (:purgeReady = false OR (c.deletedAt IS NOT NULL AND c.deletedAt <= :purgeThreshold))
        """)
    Page<Comment> searchForAdmin(@Param("keyword") String keyword,
                                 @Param("postId") Long postId,
                                 @Param("authorId") Long authorId,
                                 @Param("status") String status,
                                 @Param("createdFrom") LocalDateTime createdFrom,
                                 @Param("createdTo") LocalDateTime createdTo,
                                 @Param("purgeReady") boolean purgeReady,
                                 @Param("purgeThreshold") LocalDateTime purgeThreshold,
                                 Pageable pageable);

    long countByDeletedAtIsNull();

    @EntityGraph(attributePaths = {"user", "post"})
    List<Comment> findTop5ByDeletedAtIsNullOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"children", "post", "user", "parent", "children.user"})
    @Query("SELECT DISTINCT c FROM Comment c LEFT JOIN FETCH c.children LEFT JOIN FETCH c.post LEFT JOIN FETCH c.user LEFT JOIN FETCH c.parent WHERE c.id = :id")
    Optional<Comment> findAdminCommentById(@Param("id") Long id);

    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);

    List<Comment> findAllByPostId(Long postId);
}
