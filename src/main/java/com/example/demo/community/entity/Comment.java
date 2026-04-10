package com.example.demo.community.entity;

import com.example.demo.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comment_seq")
    @SequenceGenerator(
            name = "comment_seq",
            sequenceName = "comment_sequence",
            allocationSize = 1
    )
    private Long id;

    // 게시글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 부모 댓글 (NULL이면 최상위 댓글, 값 있으면 대댓글)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    // 대댓글 목록
    @OneToMany(mappedBy = "parent")
    @Builder.Default
    private List<Comment> children = new ArrayList<>();

    // 내용 (소프트 딜리트 시 "삭제된 댓글입니다" 표시용으로 nullable)
    @Column(columnDefinition = "CLOB")
    private String content;

    // 소프트 딜리트 (NULL이면 정상, 값 있으면 삭제 — 대댓글 고아 방지)
    private LocalDateTime deletedAt;

    // 생성일
    @Column(nullable = false)
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

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
