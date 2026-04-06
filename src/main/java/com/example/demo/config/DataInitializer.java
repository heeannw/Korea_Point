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
 * ddl-auto=create-drop 환경이므로 매 기동마다 실행됨.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) return;

        categoryRepository.saveAll(List.of(
                Category.builder().name("Food").emoji("🍜").sortOrder(1).build(),
                Category.builder().name("Transport").emoji("🚇").sortOrder(2).build(),
                Category.builder().name("Safety").emoji("🛡️").sortOrder(3).build(),
                Category.builder().name("General").emoji("💬").sortOrder(4).build()
        ));
    }
}
