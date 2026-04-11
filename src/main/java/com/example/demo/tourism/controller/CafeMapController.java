package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.CafeDto;
import com.example.demo.tourism.service.CafeService;
import com.example.demo.tourism.service.UnsplashImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * CafeMapController
 *
 * [수정 내용]
 * - UnsplashImageService 주입: 카페 이미지(커피 사진) URL을 모델에 추가
 *   → model에 "cafeImageUrl" (String) 하나만 담음
 *   → 카페는 단일 카테고리이므로 "카페" 키 1장만 사용
 */
@Controller
@RequiredArgsConstructor
public class CafeMapController {

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaokey;

    private final CafeService cafeService;
    private final UnsplashImageService unsplashImageService;   // ✅ 추가

    // ─────────────────────────────────────────────────────────────────────────
    // KO 버전
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/cafe/all")
    public String cafeAll(Model model) {
        List<CafeDto> list = cafeService.getAllCafes();
        addCommonAttributes(model, list, 37.5665, 126.9780, "", "ko");
        return "tourism/map";
    }

    @GetMapping("/cafe/search")
    public String cafeSearch(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            Model model
    ) {
        List<CafeDto> list = keyword.isBlank() ? List.of() : cafeService.analyzeByKeyword(keyword);

        double lat = 37.5665, lng = 126.9780;
        if (!list.isEmpty()) { lat = list.get(0).getLat(); lng = list.get(0).getLng(); }

        addCommonAttributes(model, list, lat, lng, keyword, "ko");
        return "tourism/map";
    }

    @GetMapping("/cafe/mylocation/{lat}/{lng}")
    public String myLocation(
            @PathVariable Double lat,
            @PathVariable Double lng,
            Model model
    ) {
        List<CafeDto> list = cafeService.analyzeLocation(lat, lng);
        addCommonAttributes(model, list, lat, lng, "", "ko");
        return "tourism/map";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EN 버전
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/en/cafe/all")
    public String cafeAllEn(Model model) {
        List<CafeDto> list = cafeService.getAllCafes();
        addCommonAttributes(model, list, 37.5665, 126.9780, "", "en");
        return "tourism/map";
    }

    @GetMapping("/en/cafe/search")
    public String cafeSearchEn(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            Model model
    ) {
        List<CafeDto> list = keyword.isBlank() ? cafeService.getAllCafes() : cafeService.analyzeByKeyword(keyword);

        double lat = 37.5665, lng = 126.9780;
        if (!list.isEmpty()) { lat = list.get(0).getLat(); lng = list.get(0).getLng(); }

        addCommonAttributes(model, list, lat, lng, keyword, "en");
        return "tourism/map";
    }

    @GetMapping("/en/cafe/mylocation/{lat}/{lng}")
    public String myLocationEn(
            @PathVariable Double lat,
            @PathVariable Double lng,
            Model model
    ) {
        List<CafeDto> list = cafeService.analyzeLocation(lat, lng);
        addCommonAttributes(model, list, lat, lng, "", "en");
        return "tourism/map";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 공통 Model 세팅 헬퍼
    // ─────────────────────────────────────────────────────────────────────────

    private void addCommonAttributes(Model model, List<CafeDto> list,
                                     double lat, double lng,
                                     String keyword, String lang) {
        model.addAttribute("kakaoKey", kakaokey);
        model.addAttribute("list", list);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lng);
        model.addAttribute("keyword", keyword);
        model.addAttribute("lang", lang);

        // ✅ Unsplash 카페 이미지 URL (캐시에서 즉시 반환, API 재호출 없음)
        model.addAttribute("cafeImageUrl", unsplashImageService.getImageUrl("카페"));
    }
}