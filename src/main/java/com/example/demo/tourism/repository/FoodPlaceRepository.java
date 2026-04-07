package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.FoodPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodPlaceRepository extends JpaRepository<FoodPlace, Long> {

    /** 이름 또는 구글명 키워드 검색 */
    @Query("SELECT f FROM FoodPlace f WHERE LOWER(f.name) LIKE LOWER(CONCAT('%',:kw,'%')) " +
            "OR LOWER(f.googleName) LIKE LOWER(CONCAT('%',:kw,'%'))")
    List<FoodPlace> findByNameContaining(@Param("kw") String keyword);

    Optional<FoodPlace> findByName(String name);

    /**
     * 위치 기반 조회 (delta ≈ 반경 km / 111)
     * 정밀 거리 필터링은 서비스 레이어(Haversine)에서 수행
     */
    @Query("SELECT f FROM FoodPlace f WHERE " +
            "ABS(f.lat - :lat) < :delta AND ABS(f.lng - :lng) < :delta")
    List<FoodPlace> findNearby(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("delta") double delta);

    /** 업종별 조회 */
    List<FoodPlace> findByCuisine(String cuisine);

    /** 위치 + 업종 복합 조회 */
    @Query("SELECT f FROM FoodPlace f WHERE f.cuisine = :cuisine AND " +
            "ABS(f.lat - :lat) < :delta AND ABS(f.lng - :lng) < :delta")
    List<FoodPlace> findNearbyByCuisine(@Param("lat") double lat,
                                        @Param("lng") double lng,
                                        @Param("delta") double delta,
                                        @Param("cuisine") String cuisine);
}