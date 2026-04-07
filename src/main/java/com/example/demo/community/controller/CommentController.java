package com.example.demo.community.controller;

import com.example.demo.community.dto.CommentDto;
import com.example.demo.community.service.CommentService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // 댓글 또는 대댓글 작성 (req.parentId == null → 댓글, 값 있음 → 대댓글)
    @PostMapping("/{postId}")
    public ResponseEntity<Map<String, Long>> addComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentDto.CommentCreateRequest req) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long commentId = commentService.addComment(postId, userId, req);
        return ResponseEntity.ok(Map.of("commentId", commentId));
    }

    // 댓글 삭제
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        Long userId = SecurityUtil.getCurrentUserId();
        commentService.deleteComment(commentId, userId);
        return ResponseEntity.ok().build();
    }
}
