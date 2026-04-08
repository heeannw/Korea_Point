package com.example.demo.tourism.service;

import com.example.demo.tourism.entity.FoodPlace;
import com.example.demo.tourism.repository.FoodPlaceRepository;
import com.example.demo.tourism.repository.FoodRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FoodPlaceService {

    private final FoodRepository jsonRepository;
    private final FoodPlaceRepository jpaRepository;

    @PostConstruct
    public void init() throws Exception {

        if (jpaRepository.count() > 0) return;

        List<FoodPlace> all = new ArrayList<>();

        // 음식점
        List<Map<String, Object>> foods = jsonRepository.loadFoodScores();

        for (Map<String, Object> f : foods) {
            all.add(FoodPlace.builder()
                    .name((String) f.get("원본이름"))
                    .googleName((String) f.get("구글이름"))
                    .lat(((Number) f.get("위도")).doubleValue())
                    .lng(((Number) f.get("경도")).doubleValue())
                    .avgStar(((Number) f.get("avg_star")).doubleValue())
                    .reviewMean(((Number) f.get("review_mean")).doubleValue())
                    .reviewCount(((Number) f.get("review_count")).intValue())
                    .finalScore(((Number) f.get("final_score")).doubleValue())
                    .category("RESTAURANT")
                    .build());
        }

        // 카페
        List<Map<String, Object>> cafes = jsonRepository.loadCafeReviews();

        Map<String, List<Map<String, Object>>> grouped =
                cafes.stream().collect(Collectors.groupingBy(r -> (String) r.get("원본이름")));

        for (String name : grouped.keySet()) {
            Map<String, Object> first = grouped.get(name).get(0);

            all.add(FoodPlace.builder()
                    .name(name)
                    .googleName((String) first.get("구글이름"))
                    .lat(((Number) first.get("위도")).doubleValue())
                    .lng(((Number) first.get("경도")).doubleValue())
                    .avgStar(((Number) first.get("평균별점")).doubleValue())
                    .reviewMean(((Number) first.get("평균별점")).doubleValue())
                    .reviewCount(grouped.get(name).size())
                    .finalScore(((Number) first.get("평균별점")).doubleValue() / 5.0)
                    .category("CAFE")
                    .build());
        }

        jpaRepository.saveAll(all);
    }

    public List<FoodPlace> getAll() {
        return jpaRepository.findAll();
    }

    public List<FoodPlace> search(String keyword) {
        return jpaRepository.findByNameContaining(keyword);
    }

    public List<FoodPlace> getByCategory(String category) {
        if ("ALL".equals(category)) return getAll();
        return jpaRepository.findByCategory(category);
    }
}
