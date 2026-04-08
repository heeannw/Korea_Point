package com.example.demo.tourism.repository;

import com.example.demo.tourism.entity.FoodPlace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodPlaceRepository extends JpaRepository<FoodPlace, Long> {

    List<FoodPlace> findByNameContaining(String keyword);

    List<FoodPlace> findByCategory(String category);
}