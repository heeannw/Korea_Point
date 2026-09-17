package com.example.demo.community.service;

import com.example.demo.config.MessageArgumentException;
import com.example.demo.config.MessageStateException;
import com.example.demo.community.dto.CommentDto;
import com.example.demo.community.entity.Comment;
import com.example.demo.community.entity.CommunityTranslationTarget;
import com.example.demo.community.entity.Post;
import com.example.demo.community.repository.CommentRepository;
import com.example.demo.community.repository.CommunityTranslationRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.user.entity.AccountStatus;
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
    private final CommunityTranslationRepository translationRepository;

    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByPost(Long postId) {
        return commentRepository.findByPostIdAndParentIsNullAndDeletedAtIsNullOrderByCreatedAtAsc(postId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public Long addComment(Long postId, Long userId, CommentDto.CommentCreateRequest req) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new MessageArgumentException("community.error.postNotFound", "존재하지 않는 게시글입니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new MessageArgumentException("community.error.userNotFound", "존재하지 않는 사용자입니다."));
        validateCommunityAccess(user);

        Comment parent = null;
        if (req.getParentId() != null) {
            parent = commentRepository.findById(req.getParentId())
                    .orElseThrow(() -> new MessageArgumentException("community.error.commentNotFound", "존재하지 않는 댓글입니다."));
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

    @Transactional
    public void updateComment(Long commentId, Long userId, String content) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new MessageArgumentException("community.error.commentNotFound", "존재하지 않는 댓글입니다."));
        if (!comment.getUser().getId().equals(userId)) {
            throw new MessageStateException("community.error.noEditPermission", "수정 권한이 없습니다.");
        }
        validateCommunityAccess(comment.getUser());
        comment.setContent(content);
        translationService.translateCommentAsync(commentId, content);
        // 옛 글로 만든 일본어·중국어·불어 번역을 지운다. 지우지 않으면 보충 작업이
        // "번역 줄이 없는 것"만 찾으므로 고친 댓글은 옛 번역을 그대로 달고 있게 된다.
        translationRepository.deleteByTargetTypeAndTargetId(
                CommunityTranslationTarget.COMMENT, commentId);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new MessageArgumentException("community.error.commentNotFound", "존재하지 않는 댓글입니다."));
        if (!comment.getUser().getId().equals(userId)) {
            throw new MessageStateException("community.error.noDeletePermission", "삭제 권한이 없습니다.");
        }
        validateCommunityAccess(comment.getUser());

        comment.setDeletedAt(LocalDateTime.now());

        if (comment.getParent() == null) {
            long childCount = comment.getChildren().stream()
                    .filter(child -> child.getDeletedAt() == null)
                    .count();
            comment.getChildren().stream()
                    .filter(child -> child.getDeletedAt() == null)
                    .forEach(child -> child.setDeletedAt(LocalDateTime.now()));
            postRepository.updateCommentCount(comment.getPost().getId(), -(int) (1 + childCount));
            return;
        }

        List<Comment> siblings = comment.getParent().getChildren();
        int cascadeCount = cascadeSoftDeleteByReplyToId(commentId, siblings);
        postRepository.updateCommentCount(comment.getPost().getId(), -(1 + cascadeCount));
    }

    private int cascadeSoftDeleteByReplyToId(Long deletedId, List<Comment> pool) {
        return cascadeSoftDeleteByReplyToId(deletedId, pool, 0);
    }

    private int cascadeSoftDeleteByReplyToId(Long deletedId, List<Comment> pool, int depth) {
        if (depth > 100) {
            return 0;
        }
        int count = 0;
        for (Comment comment : pool) {
            if (comment.getDeletedAt() == null && deletedId.equals(comment.getReplyToId())) {
                comment.setDeletedAt(LocalDateTime.now());
                count++;
                count += cascadeSoftDeleteByReplyToId(comment.getId(), pool, depth + 1);
            }
        }
        return count;
    }

    private CommentDto toDto(Comment comment) {
        List<Comment> childList = comment.getChildren().stream()
                .filter(child -> child.getDeletedAt() == null)
                .collect(Collectors.toList());

        Map<Long, Comment> idMap = childList.stream()
                .collect(Collectors.toMap(Comment::getId, value -> value));

        Map<Long, List<Comment>> adjacency = new HashMap<>();
        List<Comment> roots = new ArrayList<>();

        for (Comment child : childList) {
            Long replyToId = child.getReplyToId();
            if (replyToId == null || !idMap.containsKey(replyToId)) {
                roots.add(child);
            } else {
                adjacency.computeIfAbsent(replyToId, key -> new ArrayList<>()).add(child);
            }
        }
        roots.sort(Comparator.comparing(Comment::getCreatedAt));
        adjacency.values().forEach(list -> list.sort(Comparator.comparing(Comment::getCreatedAt)));

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
                .contentKo(deleted ? null : comment.getContentKo())
                .authorName(authorName)
                .authorProfileImage(authorProfileImage)
                .deleted(deleted)
                .createdAt(comment.getCreatedAt())
                .children(orderedChildren)
                .build();
    }

    private void dfsAddReply(Comment comment, List<CommentDto> result, Map<Long, List<Comment>> adjacency, int depth) {
        boolean deleted = comment.isDeleted();
        Long parentId = comment.getParent() != null ? comment.getParent().getId() : null;

        String rawContent = deleted ? null : comment.getContent();
        String mentionPart = null;
        String content = rawContent;
        if (rawContent != null && rawContent.startsWith("@")) {
            int spaceIndex = rawContent.indexOf(' ');
            if (spaceIndex > 0) {
                mentionPart = rawContent.substring(0, spaceIndex);
                content = rawContent.substring(spaceIndex + 1);
            }
        }

        String rawContentEn = deleted ? null : comment.getContentEn();
        String contentEn = rawContentEn;
        if (rawContentEn != null && rawContentEn.startsWith("@")) {
            int spaceIndex = rawContentEn.indexOf(' ');
            if (spaceIndex > 0) {
                contentEn = rawContentEn.substring(spaceIndex + 1);
            }
        }

        String rawContentKo = deleted ? null : comment.getContentKo();
        String contentKo = rawContentKo;
        if (rawContentKo != null && rawContentKo.startsWith("@")) {
            int spaceIndex = rawContentKo.indexOf(' ');
            if (spaceIndex > 0) {
                contentKo = rawContentKo.substring(spaceIndex + 1);
            }
        }

        result.add(CommentDto.builder()
                .id(comment.getId())
                .userId(comment.getUser().getId())
                .parentId(parentId)
                .replyToId(comment.getReplyToId())
                .depth(Math.min(depth, 4))
                .mentionPart(mentionPart)
                .content(content)
                .contentEn(contentEn)
                .contentKo(contentKo)
                .authorName(deleted ? null : comment.getUser().getName())
                .authorProfileImage(deleted ? null : comment.getUser().getProfileImage())
                .deleted(deleted)
                .createdAt(comment.getCreatedAt())
                .children(List.of())
                .build());

        for (Comment child : adjacency.getOrDefault(comment.getId(), List.of())) {
            dfsAddReply(child, result, adjacency, depth + 1);
        }
    }

    private void validateCommunityAccess(User user) {
        if (user.getAccountStatus() == AccountStatus.SUSPENDED) {
            throw new MessageStateException("community.error.suspended", "커뮤니티 기능이 정지된 계정입니다.");
        }
    }
}
