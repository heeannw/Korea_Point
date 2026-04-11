package com.example.demo.community.repository;

import com.example.demo.community.entity.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 게시글의 최상위 댓글만 조회 (parent가 null = 대댓글 아닌 것, 생성일 오름차순)
    // @EntityGraph로 children을 한 번에 JOIN 로딩 → N+1 쿼리 방지
    @EntityGraph(attributePaths = {"children"})
    List<Comment> findByPostIdAndParentIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(Long postId);
}
