package com.example.demo.reservation.repository;

import com.example.demo.reservation.entity.ReservationPlacePolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ReservationPlacePolicyRepository extends JpaRepository<ReservationPlacePolicy, String> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from ReservationPlacePolicy p where p.contentId = :id")
    Optional<ReservationPlacePolicy> findForBooking(@Param("id") String id);
}
