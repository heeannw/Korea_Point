package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.example.demo.tourism.service.FoodMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * FoodMapController
 *
 * [수정 내용]
 * foodmap.html / foodmapEn.html 두 파일을 foodmap.html 하나로 통합.
 * 컨트롤러에서 lang ("ko" | "en") 을 Model에 담아 전달하면
 * 뷰(foodmap.html) 내부에서 Thymeleaf 조건식과 JS T{} 객체로 언어를 분기합니다.
 * foodmapEn.html 파일은 더 이상 사용하지 않습니다.
 *
 *   KO:  /foodmap/all, /foodmap/search, /foodmap/mylocation/{lat}/{lng}
 *   EN:  /en/foodmap/all, /en/foodmap/search, /en/foodmap/mylocation/{lat}/{lng}
 *   API: /foodmap/api/places, /foodmap/detail/{name}  (KO/EN 공용 — 변경 없음)
 */
@Controller
@RequiredArgsConstructor
public class FoodMapController {

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;

    private final FoodMapService foodMapService;

    // ── KO 전체 ──────────────────────────────────────────────────────────────
    @GetMapping("/foodmap/all")
    public String foodmapAllKo(
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model) {
        addCommonAttributes(model, foodMapService.getAllPlaces(category),
                37.5665, 126.9780, "", category, "ko");
        return "tourism/foodmap";
    }

    // ── EN 전체 ──────────────────────────────────────────────────────────────
    @GetMapping("/en/foodmap/all")
    public String foodmapAllEn(
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model) {
        addCommonAttributes(model, foodMapService.getAllPlaces(category),
                37.5665, 126.9780, "", category, "en");
        return "tourism/foodmap";
    }

    // ── KO 검색 ───────────────────────────────────────────────────────────────
    @GetMapping("/foodmap/search")
    public String foodmapSearchKo(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model) {
        List<FoodPlaceDto> list = keyword.isBlank()
                ? foodMapService.getAllPlaces(category)
                : foodMapService.analyzeByKeyword(keyword, category);

        double lat = 37.5665, lng = 126.9780;
        if (!list.isEmpty() && list.get(0).getLat() != null) {
            lat = list.get(0).getLat();
            lng = list.get(0).getLng();
        }
        addCommonAttributes(model, list, lat, lng, keyword, category, "ko");
        return "tourism/foodmap";
    }

    // ── EN 검색 ───────────────────────────────────────────────────────────────
    @GetMapping("/en/foodmap/search")
    public String foodmapSearchEn(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model) {
        List<FoodPlaceDto> list = keyword.isBlank()
                ? foodMapService.getAllPlaces(category)
                : foodMapService.analyzeByKeyword(keyword, category);

        double lat = 37.5665, lng = 126.9780;
        if (!list.isEmpty() && list.get(0).getLat() != null) {
            lat = list.get(0).getLat();
            lng = list.get(0).getLng();
        }
        addCommonAttributes(model, list, lat, lng, keyword, category, "en");
        return "tourism/foodmap";
    }

    // ── KO 현위치 ─────────────────────────────────────────────────────────────
    @GetMapping("/foodmap/mylocation/{lat}/{lng}")
    public String foodmapMyLocationKo(
            @PathVariable Double lat,
            @PathVariable Double lng,
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model) {
        addCommonAttributes(model, foodMapService.analyzeLocation(lat, lng, category),
                lat, lng, "", category, "ko");
        return "tourism/foodmap";
    }

    // ── EN 현위치 ─────────────────────────────────────────────────────────────
    @GetMapping("/en/foodmap/mylocation/{lat}/{lng}")
    public String foodmapMyLocationEn(
            @PathVariable Double lat,
            @PathVariable Double lng,
            @RequestParam(value = "category", defaultValue = "ALL") String category,
            Model model) {
        addCommonAttributes(model, foodMapService.analyzeLocation(lat, lng, category),
                lat, lng, "", category, "en");
        return "tourism/foodmap";
    }

    // ── 공용 API: 카테고리 Ajax 전환 ─────────────────────────────────────────
    @GetMapping("/foodmap/api/places")
    @ResponseBody
    public ResponseEntity<List<FoodPlaceDto>> apiPlaces(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(value = "category", defaultValue = "ALL") String category) {
        return ResponseEntity.ok(foodMapService.getPlacesJson(lat, lng, category));
    }

    // ── 공용 API: 상세 (리뷰 포함) ───────────────────────────────────────────
    @GetMapping("/foodmap/detail/{name}")
    @ResponseBody
    public ResponseEntity<?> apiDetail(@PathVariable String name) {
        Optional<FoodPlaceDto> detail = foodMapService.getDetail(name);
        return detail.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── 공통 Model 세팅 헬퍼 ─────────────────────────────────────────────────
    private void addCommonAttributes(Model model, List<FoodPlaceDto> list,
                                     double lat, double lng,
                                     String keyword, String category, String lang) {
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("list", list);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lng);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("lang", lang);   // "ko" 또는 "en" → 뷰에서 분기 사용
    }
}