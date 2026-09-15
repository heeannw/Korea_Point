package com.example.demo.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private String thumbnailUrl;   // 본문 첫 이미지 (목록 썸네일용, nullable)
    private boolean fresh;         // 최근에 올라온 글인지 (목록 NEW 배지용)
    private boolean hot;           // 조회수가 많이 붙은 글인지 (목록 불꽃용)
    private String categoryNameEn;   // 영문 카테고리명 (nullable)
    private String titleEn;          // 영문 번역 제목 (nullable)
    private String contentEn;        // 영문 번역 내용 (nullable)
    private String contentEnPreview; // 영문 번역 미리보기 — HTML 태그 제거 + 120자 (nullable)
    private String titleKo;          // 한국어 번역 제목 (영어 작성 시, nullable)
    private String contentKo;        // 한국어 번역 내용 (영어 작성 시, nullable)
    private String contentKoPreview; // 한국어 번역 미리보기 (nullable)
    private String placeContentId;
    private String placeContentTypeId;
    private String placeTitle;

    /** 글에 붙인 내부 장소들 (상세 화면 "이 글에서 소개한 장소"). 상세 조회 때만 채운다. */
    private List<PlaceRef> places;

    /** 글 ↔ 장소 연결 한 건. 글쓰기 폼 입력과 상세 출력에 함께 쓴다. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlaceRef {
        private String contentId;
        private String contentTypeId;
        private String title;
        private String kind; // tour / food / cafe
        private Double lat;
        private Double lng;
        private String addr;
    }

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

        private String placeContentId;
        private String placeContentTypeId;
        private String placeTitle;

        /** 다중 장소 첨부. 폼에서 places[0].contentId 형태로 넘어온다. */
        private List<PlaceRef> places = new ArrayList<>();
    }
}
