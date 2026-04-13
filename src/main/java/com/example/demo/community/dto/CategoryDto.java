package com.example.demo.community.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDto {

    private Long categoryId;
    private String name;
    private String nameEn;
    private String color;
    private String icon;
}