package com.example.demo.config;

import com.example.demo.community.entity.Category;
import com.example.demo.community.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 기동 시 category 테이블이 비어있으면 기본 카테고리 4개를 자동 삽입.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) return;

        categoryRepository.saveAll(List.of(
                Category.builder().name("맛집").emoji("🍜").sortOrder(1).build(),
                Category.builder().name("교통").emoji("🚇").sortOrder(2).build(),
                Category.builder().name("관광").emoji("🛡️").sortOrder(3).build(),
                Category.builder().name("자유").emoji("💬").sortOrder(4).build(),
                Category.builder().name("홍보").emoji("💬").sortOrder(5).build()
        ));
    }
}
