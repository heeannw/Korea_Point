package com.example.demo.community.entity;

import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "post_seq")
    @SequenceGenerator(
            name = "post_seq",
            sequenceName = "post_sequence",
            allocationSize = 1
    )
    private Long id;

    // 🔥 작성자 (User랑 연관관계)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

   // 🔥 카테고리 (따로 뺀 경우)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    // 제목
    @Column(nullable = false, length = 200)
    private String title;

    // 내용
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // 영문 번역 제목 (작성 시 자동 생성, nullable)
    @Column(length = 200)
    private String titleEn;

    // 영문 번역 내용 (작성 시 자동 생성, nullable)
    @Column(columnDefinition = "text")
    private String contentEn;

    // 한국어 번역 제목 (영어로 작성 시 자동 생성, nullable)
    @Column(length = 200)
    private String titleKo;

    // 한국어 번역 내용 (영어로 작성 시 자동 생성, nullable)
    @Column(columnDefinition = "text")
    private String contentKo;

    /** TourAPI 장소 태그. 외부 장소 원문 대신 식별값과 표시용 최소 정보만 저장한다. */
    @Column(length = 40)
    private String placeContentId;

    @Column(length = 20)
    private String placeContentTypeId;

    @Column(length = 200)
    private String placeTitle;

    // 조회수
    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    // 댓글수 (역정규화 — 목록 조회 성능용)
    @Column(nullable = false)
    @Builder.Default
    private int commentCount = 0;

    /**
     * 홍보 글의 사이드바 노출 만료 시각. 홍보가 아닌 글은 null 이다.
     *
     * <p>500P 를 받고 파는 것이 '7일 노출'이라는 걸 데이터로 못박는다.
     * 이 시각이 지나면 오른쪽 레일에서만 내려오고, 목록과 홍보 탭에는 그대로 남는다.
     */
    private LocalDateTime promoUntil;

    // 생성일
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // 수정일
    private LocalDateTime updatedAt;

    // 소프트 딜리트 (NULL이면 정상, 값 있으면 삭제됨)
    private LocalDateTime deletedAt;

    // 🔥 생성 시 자동 세팅
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // 🔥 수정 시 자동 세팅
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
