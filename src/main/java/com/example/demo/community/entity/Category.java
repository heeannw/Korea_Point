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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // 화면에 표시할 이모지 (예: 🍜 🚇 🛡️ 💬)
    private String emoji;

    // 목록 표시 순서
    @Column(nullable = false)
    private int sortOrder;
}