package com.example.demo.trip.repository;

import com.example.demo.trip.entity.PlaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<PlaceEntity, Long> {

    List<PlaceEntity> findByCategory(String category);

    List<PlaceEntity> findByCategoryAndAreaContaining(String category, String area);

    @Query("SELECT p FROM PlaceEntity p WHERE p.name IN :names")
    List<PlaceEntity> findByNameIn(@Param("names") List<String> names);

    @Query("SELECT p FROM PlaceEntity p WHERE p.category = :category ORDER BY p.baseScore DESC")
    List<PlaceEntity> findTopByCategory(@Param("category") String category);
}