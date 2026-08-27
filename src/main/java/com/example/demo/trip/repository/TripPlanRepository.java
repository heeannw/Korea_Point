package com.example.demo.trip.repository;

import com.example.demo.trip.entity.TripPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripPlanRepository extends JpaRepository<TripPlan, Long> {

    /** 특정 유저의 전체 여행 계획 (최신순) */
    List<TripPlan> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 본인 소유 확인용 */
    Optional<TripPlan> findByIdAndUserId(Long id, String userId);

    /** days를 먼저 fetch하고 slots는 서비스 트랜잭션 안에서 지연 로딩한다. */
    @Query("""
        SELECT DISTINCT tp FROM TripPlan tp
        LEFT JOIN FETCH tp.days d
        WHERE tp.id = :id AND tp.userId = :userId
    """)
    Optional<TripPlan> findWithDaysAndSlots(@Param("id") Long id,
                                            @Param("userId") String userId);
}
