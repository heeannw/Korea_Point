package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.CafeDto;
import com.example.demo.tourism.service.CafeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TourismController 와 동일한 흐름:
 *
 *   /cafe/all              → 전체 지도 (빈 리스트)
 *   /cafe/search?keyword=X → 카페명 검색 → 해당 좌표 기준 2km 추천
 *   /cafe/mylocation/{lat}/{lng} → 현위치 기준 2km 추천
 */
@Controller
@RequiredArgsConstructor
public class CafeMapController {

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaokey;

    private final CafeService cafeService;

    //private static final String KAKAO_KEY = "74a1baa0b46eddc7993e7faf35451d1d";

    @GetMapping("/cafe/all")
    public String cafeAll(Model model) {
        model.addAttribute("kakaoKey", kakaokey);
        model.addAttribute("list", List.of());
        model.addAttribute("userlat", 37.5665);
        model.addAttribute("userlon", 126.9780);
        return "tourism/map";
    }

    @GetMapping("/cafe/search")
    public String cafeSearch(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            Model model
    ) {
        List<CafeDto> list = keyword.isBlank()
                ? List.of()
                : cafeService.analyzeByKeyword(keyword);

        double lat = 37.5665;
        double lng = 126.9780;

        if (!list.isEmpty()) {
            lat = list.get(0).getLat();
            lng = list.get(0).getLng();
        }

        model.addAttribute("kakaoKey", kakaokey);
        model.addAttribute("list", list);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lng);
        model.addAttribute("keyword", keyword);

        return "tourism/map";
    }

    @GetMapping("/cafe/mylocation/{lat}/{lng}")
    public String myLocation(
            @PathVariable Double lat,
            @PathVariable Double lng,
            Model model
    ) {
        List<CafeDto> list = cafeService.analyzeLocation(lat, lng);

        model.addAttribute("kakaoKey", kakaokey);
        model.addAttribute("list", list);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lng);

        return "tourism/map";
    }
}