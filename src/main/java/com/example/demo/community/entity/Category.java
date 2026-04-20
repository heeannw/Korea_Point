package com.example.demo.community.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "category_seq")
    @SequenceGenerator(name = "category_seq", sequenceName = "category_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // 영문 카테고리명
    private String nameEn;

    // 화면에 표시할 이모지 (예: 🍜 🚇 🛡️ 💬)
    private String emoji;

    // 목록 표시 순서
    @Column(nullable = false)
    private int sortOrder;

}
