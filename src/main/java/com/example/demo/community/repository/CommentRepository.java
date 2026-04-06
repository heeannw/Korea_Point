package com.example.demo.community.repository;

import com.example.demo.community.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 게시글의 최상위 댓글만 조회 (parent가 null = 대댓글 아닌 것, 생성일 오름차순)
    List<Comment> findByPostIdAndParentIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(Long postId);
}
