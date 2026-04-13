package com.example.demo.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

// 게시글 응답 DTO (목록/상세 조회)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostDto {

    private Long postId;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private String categoryEmoji;
    private String title;
    private String content;
    private String authorName;
    private String authorProfileImage;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;
    private String contentPreview; // HTML 태그 제거 + 120자 요약 (목록용)
    private String categoryNameEn;   // 영문 카테고리명 (nullable)
    private String titleEn;          // 영문 번역 제목 (nullable)
    private String contentEn;        // 영문 번역 내용 (nullable)
    private String contentEnPreview; // 영문 번역 미리보기 — HTML 태그 제거 + 120자 (nullable)

    // 게시글 작성 요청 DTO
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PostCreateRequest {

        @NotBlank(message = "제목을 입력해주세요.")
        private String title;

        @NotBlank(message = "내용을 입력해주세요.")
        private String content;

        private Long categoryId;
    }
}