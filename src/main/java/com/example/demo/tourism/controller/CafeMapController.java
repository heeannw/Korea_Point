package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.CafeDto;
import com.example.demo.tourism.service.CafeService;
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
 * map.html / mapEn.html 두 파일을 map.html 하나로 통합하였습니다.
 * 컨트롤러에서 lang ("ko" | "en") 을 Model에 담아 전달하면,
 * 뷰(map.html) 내부에서 Thymeleaf 조건식과 JS T{} 객체로 언어를 분기합니다.
 * mapEn.html 파일은 더 이상 사용하지 않습니다.
 *
 *   /cafe/all                   → 전체 카페 (KO)
 *   /cafe/search?keyword=X      → 키워드 검색 2km 추천 (KO)
 *   /cafe/mylocation/{lat}/{lng} → 현위치 기준 2km 추천 (KO)
 *
 *   /en/cafe/all                   → 전체 카페 (EN)
 *   /en/cafe/search?keyword=X      → 키워드 검색 2km 추천 (EN)
 *   /en/cafe/mylocation/{lat}/{lng} → 현위치 기준 2km 추천 (EN)
 */
@Controller
@RequiredArgsConstructor
public class CafeMapController {

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaokey;

    private final CafeService cafeService;

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
    // EN 버전 — 같은 뷰(tourism/map)를 사용하고 lang="en" 만 다르게 전달
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
        model.addAttribute("lang", lang);   // "ko" 또는 "en" → 뷰에서 분기 사용
    }
}