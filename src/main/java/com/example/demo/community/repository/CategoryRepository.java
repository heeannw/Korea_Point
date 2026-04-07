package com.example.demo.community.repository;

import com.example.demo.community.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Category 조회를 담당하는 Repository (파일명은 기존 구조 유지)
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // 정렬 순서대로 카테고리 전체 조회
    List<Category> findAllByOrderBySortOrderAsc();
}
