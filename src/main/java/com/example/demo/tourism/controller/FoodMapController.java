package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.FoodPlaceDto;
import com.example.demo.tourism.service.FoodMapService;
import com.example.demo.tourism.service.UnsplashImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FoodMapController
 *
 * [수정 내용]
 * - UnsplashImageService 주입: 카테고리별 이미지 URL 맵을 모델에 추가
 *   → model에 "foodImageMap" (Map<String, String>) 추가
 *   → JS에서 FOOD_IMAGE_MAP["한식"] 형태로 접근
 *   → 카테고리: 한식 / 중식 / 일식 / 양식 / 고기_해산물 / 바 / 기타
 */
@Controller
@RequiredArgsConstructor
public class FoodMapController {

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;

    private final FoodMapService foodMapService;
    private final UnsplashImageService unsplashImageService;   // ✅ 추가

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
        model.addAttribute("lang", lang);

        // ✅ Unsplash 카테고리별 이미지 맵 (캐시에서 즉시 반환, API 재호출 없음)
        // JS에서: FOOD_IMAGE_MAP["한식"], FOOD_IMAGE_MAP["중식"] ...
        Map<String, String> imageMap = unsplashImageService.getAllImageUrls();
        model.addAttribute("foodImageMap", imageMap);
    }
}