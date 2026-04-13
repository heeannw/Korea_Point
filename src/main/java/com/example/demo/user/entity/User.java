package com.example.demo.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq")
    @SequenceGenerator(name = "user_seq", sequenceName = "user_sequence", allocationSize = 1)
    private Long id;

    // OAuth 공급자 ('google', 'kakao' 등 — 확장 대비)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String provider = "google";

    // OAuth 공급자가 발급한 고유 ID
    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    // 사용자 이름
    @Column(nullable = false)
    private String name;

    // 이메일
    @Column(nullable = false)
    private String email;

    // 프로필 이미지
    private String profileImage;

    // 권한 ('USER', 'ADMIN')
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String role = "USER";

    // 보유 포인트 (가입 시 1000P 지급, 홍보 게시글 작성 시 차감)
    @Column(nullable = false)
    @Builder.Default
    private int point = 1000;

    // 생성일
    private LocalDateTime createdAt;

    // 수정일
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}