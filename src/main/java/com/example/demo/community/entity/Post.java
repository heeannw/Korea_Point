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
    @Lob
    @Column(nullable = false)
    private String content;

    // 영문 번역 제목 (작성 시 자동 생성, nullable)
    @Column(length = 200)
    private String titleEn;

    // 영문 번역 내용 (작성 시 자동 생성, nullable)
    @Lob
    private String contentEn;

    // 조회수
    @Column(nullable = false)
    @Builder.Default
    private int viewCount = 0;

    // 댓글수 (역정규화 — 목록 조회 성능용)
    @Column(nullable = false)
    @Builder.Default
    private int commentCount = 0;

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