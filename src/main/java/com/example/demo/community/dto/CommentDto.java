package com.example.demo.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

// 댓글/대댓글 응답 DTO
@Getter
@Builder
public class CommentDto {

    private Long id;
    private Long parentId;
    private String content;         // 소프트 딜리트 시 null → 화면에서 "삭제된 댓글입니다" 처리
    private String authorName;
    private String authorProfileImage;
    private boolean deleted;
    private LocalDateTime createdAt;
    private List<CommentDto> children;  // 대댓글 목록

    // 댓글/대댓글 작성 요청 DTO
    @Getter
    @NoArgsConstructor
    public static class CommentCreateRequest {

        @NotBlank(message = "내용을 입력해주세요.")
        private String content;

        private Long parentId;  // null이면 최상위 댓글, 값 있으면 대댓글
    }
}
