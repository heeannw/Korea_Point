package com.example.demo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(length = 100)
    private String password;

    // 사용자 이름
    @Column(nullable = false)
    private String name;

    // 이메일
    @Column(nullable = false)
    private String email;

    // 프로필 이미지
    private String profileImage;

    // 선택 입력: 사용자의 국적/국가 코드
    @Column(length = 2)
    private String nationality;

    // 첫 가입 후 국적 선택 모달을 이미 처리했는지 여부
    @Column(name = "nationality_prompted")
    @Builder.Default
    private Boolean nationalityPrompted = true;

    // 권한 ('USER', 'ADMIN')
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String role = "USER";

    // 사업자 홍보 게시글 작성 시 사용하는 포인트
    @Column(nullable = false)
    @Builder.Default
    private int point = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

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
