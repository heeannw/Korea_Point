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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final TranslationService translationService;

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
                .replyToId(req.getReplyToId())
                .content(req.getContent())
                .build();

        Comment saved = commentRepository.save(comment);
        translationService.translateCommentAsync(saved.getId(), req.getContent());
        postRepository.updateCommentCount(postId, 1);

        return comment.getId();
    }

    // 댓글 수정
    @Transactional
    public void updateComment(Long commentId, Long userId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));
        if (!comment.getUser().getId().equals(userId)) {
            throw new IllegalStateException("수정 권한이 없습니다.");
        }
        comment.setContent(content);
        translationService.translateCommentAsync(commentId, content);
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

        if (comment.getParent() == null) {
            // 최상위 댓글: 살아있는 대댓글도 모두 소프트 딜리트
            long childCount = comment.getChildren().stream()
                    .filter(c -> c.getDeletedAt() == null)
                    .count();
            comment.getChildren().stream()
                    .filter(c -> c.getDeletedAt() == null)
                    .forEach(c -> c.setDeletedAt(LocalDateTime.now()));
            postRepository.updateCommentCount(comment.getPost().getId(), -(int)(1 + childCount));
        } else {
            // 대댓글: replyToId 체인을 따라 하위 대댓글도 cascade 소프트 딜리트
            List<Comment> siblings = comment.getParent().getChildren();
            int cascadeCount = cascadeSoftDeleteByReplyToId(commentId, siblings);
            postRepository.updateCommentCount(comment.getPost().getId(), -(1 + cascadeCount));
        }
    }

    // replyToId 기준으로 하위 대댓글을 재귀적으로 소프트 딜리트, 삭제된 수 반환
    private int cascadeSoftDeleteByReplyToId(Long deletedId, List<Comment> pool) {
        return cascadeSoftDeleteByReplyToId(deletedId, pool, 0);
    }

    private int cascadeSoftDeleteByReplyToId(Long deletedId, List<Comment> pool, int depth) {
        if (depth > 100) return 0;
        int count = 0;
        for (Comment c : pool) {
            if (c.getDeletedAt() == null && deletedId.equals(c.getReplyToId())) {
                c.setDeletedAt(LocalDateTime.now());
                count++;
                count += cascadeSoftDeleteByReplyToId(c.getId(), pool, depth + 1);
            }
        }
        return count;
    }

    // Comment → CommentDto 변환 (대댓글 DFS 정렬 포함)
    private CommentDto toDto(Comment comment) {
        List<Comment> childList = comment.getChildren().stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toList());

        // id → Comment 맵
        Map<Long, Comment> idMap = childList.stream()
                .collect(Collectors.toMap(Comment::getId, c -> c));

        // adjacency: replyToId → 그 댓글에 달린 대댓글들 (시간순)
        Map<Long, List<Comment>> adjacency = new HashMap<>();
        List<Comment> roots = new ArrayList<>();

        for (Comment c : childList) {
            Long rId = c.getReplyToId();
            if (rId == null || !idMap.containsKey(rId)) {
                roots.add(c);
            } else {
                adjacency.computeIfAbsent(rId, k -> new ArrayList<>()).add(c);
            }
        }
        roots.sort(Comparator.comparing(Comment::getCreatedAt));
        adjacency.values().forEach(list -> list.sort(Comparator.comparing(Comment::getCreatedAt)));

        // DFS 순서로 평탄화 — 각 답글 바로 뒤에 그 답글의 하위 답글이 위치
        List<CommentDto> orderedChildren = new ArrayList<>();
        for (Comment root : roots) {
            dfsAddReply(root, orderedChildren, adjacency, 1);
        }

        boolean deleted = comment.isDeleted();
        Long parentId = comment.getParent() != null ? comment.getParent().getId() : null;
        String content = deleted ? null : comment.getContent();
        String authorName = deleted ? null : comment.getUser().getName();
        String authorProfileImage = deleted ? null : comment.getUser().getProfileImage();

        return CommentDto.builder()
                .id(comment.getId())
                .userId(comment.getUser().getId())
                .parentId(parentId)
                .content(content)
                .contentEn(deleted ? null : comment.getContentEn())
                .authorName(authorName)
                .authorProfileImage(authorProfileImage)
                .deleted(deleted)
                .createdAt(comment.getCreatedAt())
                .children(orderedChildren)
                .build();
    }

    // DFS — 대댓글 하나를 result에 추가하고, 그 답글들도 재귀적으로 추가
    private void dfsAddReply(Comment c, List<CommentDto> result,
                              Map<Long, List<Comment>> adjacency, int depth) {
        boolean deleted = c.isDeleted();
        Long parentId = c.getParent() != null ? c.getParent().getId() : null;

        String rawContent = deleted ? null : c.getContent();
        String mentionPart = null;
        String content = rawContent;
        if (rawContent != null && rawContent.startsWith("@")) {
            int spaceIdx = rawContent.indexOf(' ');
            if (spaceIdx > 0) {
                mentionPart = rawContent.substring(0, spaceIdx);
                content = rawContent.substring(spaceIdx + 1);
            }
        }
        String rawContentEn = deleted ? null : c.getContentEn();
        String contentEn = rawContentEn;
        if (rawContentEn != null && rawContentEn.startsWith("@")) {
            int spaceIdxEn = rawContentEn.indexOf(' ');
            if (spaceIdxEn > 0) contentEn = rawContentEn.substring(spaceIdxEn + 1);
        }
        String authorName = deleted ? null : c.getUser().getName();
        String authorProfileImage = deleted ? null : c.getUser().getProfileImage();

        result.add(CommentDto.builder()
                .id(c.getId())
                .userId(c.getUser().getId())
                .parentId(parentId)
                .replyToId(c.getReplyToId())
                .depth(Math.min(depth, 4))
                .mentionPart(mentionPart)
                .content(content)
                .contentEn(contentEn)
                .authorName(authorName)
                .authorProfileImage(authorProfileImage)
                .deleted(deleted)
                .createdAt(c.getCreatedAt())
                .children(List.of())
                .build());

        List<Comment> subtree = adjacency.getOrDefault(c.getId(), List.of());
        for (Comment child : subtree) {
            dfsAddReply(child, result, adjacency, depth + 1);
        }
    }
}
