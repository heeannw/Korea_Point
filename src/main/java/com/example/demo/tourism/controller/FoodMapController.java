package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.example.demo.tourism.service.FoodMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FoodMapController
 *
 * URL 설계 (CafeMapController / TourismController 와 동일한 흐름)
 *
 *   GET /foodmap/all                         → 전체 지도 (빈 리스트, 자동 현위치 요청)
 *   GET /foodmap/search?keyword=X&category=Y → 키워드 검색 → 해당 좌표 기준 2km 추천
 *   GET /foodmap/mylocation/{lat}/{lng}       → 현위치 기준 2km 추천
 *                                              (?category=ALL|CAFE|RESTAURANT, 기본 ALL)
 *
 * category 파라미터:
 *   ALL        = 카페 + 음식점 전체
 *   CAFE       = 카페만
 *   RESTAURANT = 음식점만
 */
@Controller
@RequiredArgsConstructor
public class FoodMapController {

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;

    private final FoodMapService foodMapService;

    // ── 전체 지도 ─────────────────────────────────────────────────────────────
    @GetMapping("/foodmap/all")
    public String foodmapAll(Model model) {
        model.addAttribute("kakaoKey",  kakaoKey);
        model.addAttribute("list",      List.of());
        model.addAttribute("userlat",   37.5665);
        model.addAttribute("userlon",   126.9780);
        model.addAttribute("category",  "ALL");
        model.addAttribute("keyword",   "");
        return "tourism/foodmap";
    }

    // ── 키워드 검색 ───────────────────────────────────────────────────────────
    @GetMapping("/foodmap/search")
    public String foodmapSearch(
            @RequestParam(value = "keyword",  defaultValue = "") String keyword,
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model
    ) {
        String cat = resolveCategory(category);

        List<FoodPlaceDto> list = keyword.isBlank()
                ? List.of()
                : foodMapService.analyzeByKeyword(keyword, cat);

        double lat = 37.5665;
        double lng = 126.9780;
        if (!list.isEmpty()) {
            lat = list.get(0).getLat();
            lng = list.get(0).getLng();
        }

        model.addAttribute("kakaoKey",  kakaoKey);
        model.addAttribute("list",      list);
        model.addAttribute("userlat",   lat);
        model.addAttribute("userlon",   lng);
        model.addAttribute("category",  cat);
        model.addAttribute("keyword",   keyword);
        return "tourism/foodmap";
    }

    // ── 현위치 기반 추천 ──────────────────────────────────────────────────────
    @GetMapping("/foodmap/mylocation/{lat}/{lng}")
    public String myLocation(
            @PathVariable Double lat,
            @PathVariable Double lng,
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model
    ) {
        String cat = resolveCategory(category);
        List<FoodPlaceDto> list = foodMapService.analyzeLocation(lat, lng, cat);

        model.addAttribute("kakaoKey",  kakaoKey);
        model.addAttribute("list",      list);
        model.addAttribute("userlat",   lat);
        model.addAttribute("userlon",   lng);
        model.addAttribute("category",  cat);
        model.addAttribute("keyword",   "");
        return "tourism/foodmap";
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────
    private String resolveCategory(String raw) {
        if ("CAFE".equalsIgnoreCase(raw))       return "CAFE";
        if ("RESTAURANT".equalsIgnoreCase(raw)) return "RESTAURANT";
        return "ALL";
    }
}