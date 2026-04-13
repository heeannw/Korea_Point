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
    private Long userId;
    private Long parentId;
    private Long replyToId;          // 대댓글 내 직접 답글 대상 (들여쓰기 계산용)
    private int depth;               // 들여쓰기 단계 (1부터 시작, 최대 4)
    private String mentionPart;      // content 앞의 @이름 부분 (파란 태그로 표시용), null이면 없음
    private String content;         // 소프트 딜리트 시 null → 화면에서 "삭제된 댓글입니다" 처리
    private String contentEn;       // 영문 번역 내용 (nullable — null이면 content 원문 표시)
    private String authorName;
    private String authorProfileImage;
    private boolean deleted;
    private LocalDateTime createdAt;
    private List<CommentDto> children;  // 대댓글 목록

    // 댓글/대댓글 작성 요청 DTO
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CommentCreateRequest {

        @NotBlank(message = "내용을 입력해주세요.")
        private String content;

        private Long parentId;   // null이면 최상위 댓글, 값 있으면 대댓글
        private Long replyToId;  // 대댓글 내 어떤 댓글에 답글을 달았는지
    }

    // 댓글 수정 요청 DTO
    @Getter
    @Setter
    @NoArgsConstructor
    public static class CommentUpdateRequest {

        @NotBlank(message = "내용을 입력해주세요.")
        private String content;
    }
}
