package com.example.demo.community.dto;

import java.time.LocalDateTime;

/**
 * 불꽃 기준을 잡을 때만 쓰는 최소 정보.
 *
 * <p>기준을 재려고 글 전체를 엔티티로 불러오면 본문까지 딸려 온다. 필요한 건
 * 조회수와 올라온 시각 두 개뿐이라 그것만 꺼낸다.
 */
public record PostViewStat(int viewCount, LocalDateTime createdAt) {
}
