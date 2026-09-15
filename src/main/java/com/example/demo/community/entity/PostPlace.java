package com.example.demo.community.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 커뮤니티 글에 붙인 내부 장소(관광지·맛집·카페). 글 1개에 여러 개 붙는다.
 *
 * <p>{@code post.place_content_id} 는 글 1개당 장소 1개만 담던 기존 컬럼이라 그대로 두고,
 * 이 표를 더해 다중 장소를 다룬다. 상세 패널 역참조({@code /tour/api/community/{contentId}})는
 * 두 곳을 모두 조회한다. {@code Post} 와 JPA 연관관계를 걸지 않고 {@code postId} 값만 들고 있어
 * 게시글 엔티티(다른 파트 소유)를 건드리지 않는다.
 */
@Entity
@Table(name = "post_place", indexes = {
        @Index(name = "idx_post_place_post", columnList = "post_id"),
        @Index(name = "idx_post_place_content", columnList = "content_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    /** TourAPI contentId — 관광·맛집·카페 공통 식별값. */
    @Column(name = "content_id", nullable = false, length = 40)
    private String contentId;

    @Column(name = "content_type_id", length = 20)
    private String contentTypeId;

    @Column(length = 200)
    private String title;

    /** tour / food / cafe — 상세 링크 목적지(/tour/all · /tour/food · /tour/cafe)를 정한다. */
    @Column(length = 10)
    private String kind;

    /** 글쓰기/상세 화면의 첨부 장소 지도(카카오맵)에 핀을 찍기 위한 좌표. */
    private Double lat;
    private Double lng;

    /** 네이버 블로그 장소 첨부처럼 지도 밑 목록에 주소를 같이 보여준다. */
    @Column(length = 300)
    private String addr;

    /** 글 안에서의 표시 순서. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
