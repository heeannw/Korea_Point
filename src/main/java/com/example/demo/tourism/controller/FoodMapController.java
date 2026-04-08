package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.example.demo.tourism.service.FoodPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/foodmap")
public class FoodMapController {

    private final FoodPlaceService service;

    // ── /foodmap/all : 전체 목록 (거리 없음, 서울 중심 기본값) ──────────────────
    @GetMapping("/all")
    public String all(Model model) {
        List<FoodPlaceDto> list = service.getAllAsDto(37.5665, 126.9780);
        model.addAttribute("list",     list);
        model.addAttribute("category", "ALL");
        model.addAttribute("userlat",  37.5665);
        model.addAttribute("userlon",  126.9780);
        return "foodmap";
    }

    // ── /foodmap/mylocation/{lat}/{lng} : 현위치 기반 추천 ──────────────────────
    @GetMapping("/mylocation/{lat}/{lng}")
    public String myLocation(
            @PathVariable double lat,
            @PathVariable double lng,
            @RequestParam(defaultValue = "ALL") String category,
            Model model) {

        List<FoodPlaceDto> list = service.getNearbyAsDto(lat, lng, category);
        model.addAttribute("list",     list);
        model.addAttribute("category", category);
        model.addAttribute("userlat",  lat);
        model.addAttribute("userlon",  lng);
        return "foodmap";
    }

    // ── /foodmap/search?keyword=...&category=... ───────────────────────────────
    @GetMapping("/search")
    public String search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "37.5665") double lat,
            @RequestParam(defaultValue = "126.9780") double lng,
            Model model) {

        List<FoodPlaceDto> list = service.searchAsDto(keyword, category, lat, lng);
        model.addAttribute("list",     list);
        model.addAttribute("keyword",  keyword);
        model.addAttribute("category", category);
        model.addAttribute("userlat",  lat);
        model.addAttribute("userlon",  lng);
        return "foodmap";
    }
}