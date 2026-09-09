package com.example.demo.tourism.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관광 개인화 추천의 콜드스타트용 선호 카테고리.
 * 첫 로그인/첫 이용 때 2~3개 고르게 한다({@code nature,culture,food,shopping,activity}).
 * 행동 로그가 쌓이기 전까지 이 값으로 초기 추천을 만든다.
 *
 * <p>{@code User} 테이블을 건드리지 않으려고 별도 테이블(user_id 를 그대로 PK 로).</p>
 */
@Entity
@Table(name = "user_tour_pref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTourPref {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** 쉼표로 이은 슬러그. 예: "nature,culture,food". */
    @Column(length = 120)
    private String categories;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
