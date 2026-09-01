package com.example.demo.reservation.repository;

import com.example.demo.reservation.entity.ReservationPlaceTranslation;
import com.example.demo.reservation.entity.ReservationTranslationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationPlaceTranslationRepository extends JpaRepository<ReservationPlaceTranslation, Long> {
    Optional<ReservationPlaceTranslation> findByContentIdAndLanguage(String contentId, String language);

    List<ReservationPlaceTranslation> findTop20ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            ReservationTranslationStatus status, LocalDateTime now);
}
