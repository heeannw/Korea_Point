package com.example.demo.reservation.repository;

import com.example.demo.reservation.entity.TourReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TourReservationRepository extends JpaRepository<TourReservation, Long> {
    List<TourReservation> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<TourReservation> findByIdAndUserId(Long id, Long userId);
}
