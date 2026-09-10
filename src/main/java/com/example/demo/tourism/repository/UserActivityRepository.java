package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.UserActivity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    /** 취향 프로필 계산에 쓸 최근 행동 (Pageable 로 상한). */
    List<UserActivity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** 오래된 로그 정리용. */
    @Modifying
    @Query("DELETE FROM UserActivity a WHERE a.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
