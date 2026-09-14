package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.TourPlaceTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TourPlaceTranslationRepository extends JpaRepository<TourPlaceTranslation, Long> {

    Optional<TourPlaceTranslation> findByContentIdAndLanguage(String contentId, String language);

    boolean existsByContentIdAndLanguage(String contentId, String language);
}
