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
}
