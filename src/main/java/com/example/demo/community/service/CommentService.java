package com.example.demo.community.service;

import com.example.demo.community.dto.CommentDto;
import com.example.demo.community.entity.Comment;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // 게시글의 댓글 + 대댓글 트리 조회
    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByPost(Long postId) {
        return commentRepository
                .findByPostIdAndParentIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(postId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // 댓글 또는 대댓글 작성
    @Transactional
    public Long addComment(Long postId, Long userId, CommentDto.CommentCreateRequest req) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Comment parent = null;
        if (req.getParentId() != null) {
            parent = commentRepository.findById(req.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));
        }

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .parent(parent)
                .content(req.getContent())
                .build();

        commentRepository.save(comment);

        // 최상위 댓글만 commentCount에 반영 (대댓글은 미포함 — UX 정책에 따라 변경 가능)
        if (parent == null) {
            postRepository.updateCommentCount(postId, 1);
        }

        return comment.getId();
    }

    // 댓글 소프트 딜리트
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));
        if (!comment.getUser().getId().equals(userId)) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }
        comment.setDeletedAt(LocalDateTime.now());

        // 최상위 댓글 삭제 시 commentCount 감소
        if (comment.getParent() == null) {
            postRepository.updateCommentCount(comment.getPost().getId(), -1);
        }
    }

    // Comment → CommentDto 변환 (대댓글 포함)
    private CommentDto toDto(Comment comment) {
        List<CommentDto> children = comment.getChildren().stream()
                .filter(c -> c.getDeletedAt() == null)
                .map(this::toDto)
                .toList();

        boolean deleted = comment.isDeleted();
        Long parentId = comment.getParent() != null ? comment.getParent().getId() : null;

        // 삭제된 댓글은 내용과 작성자 정보를 노출하지 않음
        String content = deleted ? null : comment.getContent();
        String authorName = deleted ? null : comment.getUser().getName();
        String authorProfileImage = deleted ? null : comment.getUser().getProfileImage();

        return CommentDto.builder()
                .id(comment.getId())
                .parentId(parentId)
                .content(content)
                .authorName(authorName)
                .authorProfileImage(authorProfileImage)
                .deleted(deleted)
                .createdAt(comment.getCreatedAt())
                .children(children)
                .build();
    }
}
