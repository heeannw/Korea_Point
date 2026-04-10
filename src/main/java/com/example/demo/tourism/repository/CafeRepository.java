package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.Cafe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CafeRepository extends JpaRepository<Cafe, Long> {

    /** 이름 또는 구글명에 키워드 포함 (대소문자 무시) */
    @Query("SELECT c FROM Cafe c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%',:kw,'%')) " +
            "OR LOWER(c.googleName) LIKE LOWER(CONCAT('%',:kw,'%'))")
    List<Cafe> findByNameContaining(@Param("kw") String keyword);

    Optional<Cafe> findByName(String name);

    /**
     * 위치 기반 조회 (Haversine 근사 — 소규모 범위용)
     * 정밀 거리 계산은 서비스 레이어에서 수행
     */
    @Query("SELECT c FROM Cafe c WHERE " +
            "ABS(c.lat - :lat) < :delta AND ABS(c.lng - :lng) < :delta")
    List<Cafe> findNearby(@Param("lat") double lat,
                          @Param("lng") double lng,
                          @Param("delta") double delta);
}