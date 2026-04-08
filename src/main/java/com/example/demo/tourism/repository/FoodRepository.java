package com.example.demo.tourism.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Repository
public class FoodRepository {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> loadFoodScores() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("model/tourism/food/food_scores.json");

        Map<String, Object> root =
                objectMapper.readValue(resource.getInputStream(), Map.class);

        return (List<Map<String, Object>>) root.get("food_scores");
    }

    public List<Map<String, Object>> loadCafeReviews() throws Exception {
        InputStream is = new ClassPathResource(
                "model/tourism/cafe/review_cafe.json"
        ).getInputStream();

        return objectMapper.readValue(
                is,
                new TypeReference<List<Map<String, Object>>>() {}
        );
    }
}