package com.example.demo.trip.repository;

import com.example.demo.trip.entity.TripReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripReservationRepository extends JpaRepository<TripReservation, Long> {
    List<TripReservation> findByUserIdOrderByCreatedAtDesc(String userId);
    List<TripReservation> findAllByOrderByCreatedAtDesc();
    Optional<TripReservation> findByIdAndUserId(Long id, String userId);
}
