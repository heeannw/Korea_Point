package com.example.demo.reservation.repository;

import com.example.demo.reservation.entity.TourReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface TourReservationRepository extends JpaRepository<TourReservation, Long> {
    @EntityGraph(attributePaths = "payment")
    List<TourReservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "payment")
    Optional<TourReservation> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from TourReservation r where r.id = :id")
    Optional<TourReservation> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = "payment")
    @org.springframework.data.jpa.repository.Query("select r from TourReservation r left join r.payment p where " +
            "(:pendingOnly = false or ((r.reviewStatus is null or r.reviewStatus = :pending) " +
            "and r.status not in ('예약 취소', '예약 완료') and (p is null or p.status <> :paid)))")
    org.springframework.data.domain.Page<TourReservation> findReviewQueue(
            @org.springframework.data.repository.query.Param("pendingOnly") boolean pendingOnly,
            @org.springframework.data.repository.query.Param("pending") com.example.demo.reservation.entity.ReservationReviewStatus pending,
            @org.springframework.data.repository.query.Param("paid") com.example.demo.reservation.entity.PaymentStatus paid,
            org.springframework.data.domain.Pageable pageable);
}
