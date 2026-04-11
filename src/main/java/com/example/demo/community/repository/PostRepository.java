package com.example.demo.community.repository;

import com.example.demo.community.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 전체 목록 (삭제되지 않은 것, 페이징)
    Page<Post> findByDeletedAtIsNull(Pageable pageable);

    // 카테고리별 조회 (페이징)
    Page<Post> findByCategoryIdAndDeletedAtIsNull(Long categoryId, Pageable pageable);

    // 제목 검색 (전체 카테고리, 페이징)
    Page<Post> findByTitleContainingIgnoreCaseAndDeletedAtIsNull(String keyword, Pageable pageable);

    // 카테고리 + 제목 검색 동시 적용 (페이징)
    Page<Post> findByCategoryIdAndTitleContainingIgnoreCaseAndDeletedAtIsNull(Long categoryId, String keyword, Pageable pageable);

    // 인기글 상위 5개 (조회수 기준)
    List<Post> findTop5ByDeletedAtIsNullOrderByViewCountDesc();

    // 단건 조회 (삭제되지 않은 것)
    Optional<Post> findByIdAndDeletedAtIsNull(Long id);

    // 조회수 +1
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // 댓글수 증감
    @Modifying
    @Query("UPDATE Post p SET p.commentCount = p.commentCount + :delta WHERE p.id = :id")
    void updateCommentCount(@Param("id") Long id, @Param("delta") int delta);
}
