package com.example.demo.community.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 글과 댓글의 일본어·중국어·불어 번역.
 *
 * <p>글 표에는 국문과 영문 칸만 있다({@code title_en}, {@code title_ko}). 글을 올리면
 * 번역기가 국문↔영문 한 방향만 옮긴다. 그래서 일본어로 커뮤니티를 열면 화면 틀만
 * 일본어이고 글은 한국어로 남았다.
 *
 * <p>언어마다 칸을 더 만들면 언어가 늘 때마다 표를 고쳐야 하므로 줄로 쌓는다.
 * 예약 쪽 {@code reservation_place_translation} 과 같은 방식이다.
 *
 * <p>글은 제목과 본문을 함께 담고, 댓글은 본문만 담는다.
 */
@Entity
@Table(name = "community_translation",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"target_type", "target_id", "language"}),
        indexes = @Index(name = "idx_community_translation_lookup",
                columnList = "target_type, language, target_id"))
@Getter
@Setter
@NoArgsConstructor
public class CommunityTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private CommunityTranslationTarget targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /** ja / zh / fr. 국문은 원문이고 영문은 글 표에 있어서 여기 담지 않는다. */
    @Column(nullable = false, length = 10)
    private String language;

    @Column(length = 400)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public CommunityTranslation(CommunityTranslationTarget targetType, Long targetId,
                                String language, String title, String content) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.language = language;
        this.title = title;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
