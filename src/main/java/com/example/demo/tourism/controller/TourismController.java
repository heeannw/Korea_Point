package com.example.demo.tourism.controller;

import com.example.demo.config.CurrentLanguage;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourismService;
import com.example.demo.user.util.SecurityUtil;                // ✅ 추가
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class TourismController {

    private static final double BUSAN_CENTER_LAT = 35.1796;
    private static final double BUSAN_CENTER_LON = 129.0756;

    /** 관광 목록·검색·내위치 화면은 언어와 무관하게 이 단일 템플릿을 쓴다. */
    private static final String EXPLORE_VIEW = "tourism/tourism";


    @Autowired
    TourismService tourismService;

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;

    private static final int MAX_RECENT_KEYWORDS = 10;

    // ─────────────────────────────────────────────────────────
    // 메인 탐색 페이지
    // ─────────────────────────────────────────────────────────
    @RequestMapping("/tour/all")
    public String tourAllKO(Model model, HttpSession session,
                            @RequestParam(value = "lat", required = false) Double lat,
                            @RequestParam(value = "lon", required = false) Double lon,
                            @RequestParam(value = "near", required = false) String near) {
        // 언어는 세션에서 읽는다. 예전엔 lang 을 필수로 받아 링크에 빠지면 400 흰 화면이 떴다.
        String lang = CurrentLanguage.get();
        List<TourPlaceDto> displayList = tourismService.allList(lang);
        session.setAttribute("userlat", lat);
        session.setAttribute("userlon", lon);


        List<TourPlaceDto> dlist = (lat != null && lon != null)
                ? tourismService.allDistence(lat, lon, displayList)
                : displayList;

        String spotname = "en".equalsIgnoreCase(lang) ? "My Location" : "내위치";

        // 지도 중심은 항상 부산이다. 현위치는 거리 계산에만 쓴다.
        model.addAttribute("centerLat", BUSAN_CENTER_LAT);
        model.addAttribute("centerLon", BUSAN_CENTER_LON);

        model.addAttribute("spotname", spotname);
        model.addAttribute("near", near);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lon);
        model.addAttribute("lang", lang);
        model.addAttribute("list", dlist);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());               // ✅ 추가
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());  // ✅ 추가
        model.addAttribute("currentUserId", SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserId() : null);

        return EXPLORE_VIEW;
    }

    // ─────────────────────────────────────────────────────────
    // 키워드 검색 + 내 위치까지 거리 표시
    // ─────────────────────────────────────────────────────────
    @RequestMapping("/tour/search/")
    public String tourSearch(
            @RequestParam(required = false) String keyword,
            Model model,
            @RequestParam(value = "spotname", required = false) String spotname,
            HttpSession session) {

        String lang = CurrentLanguage.get();
        if (spotname == null || spotname.isBlank()) {
            spotname = "en".equalsIgnoreCase(lang) ? "My Location" : "내위치";
        }

        Double userlat = (Double) session.getAttribute("userlat");
        Double userlon = (Double) session.getAttribute("userlon");
        List<TourPlaceDto> list;

        if (keyword != null && !keyword.isBlank()) {
            saveKeyword(session, keyword.trim());
            list = tourismService.searchKeyword(keyword.trim(), userlat, userlon, lang);

            if (list.isEmpty()) {
                list = tourismService.allList(lang);
                if (userlat != null && userlon != null) {
                    tourismService.allDistence(userlat, userlon, list);
                }
                model.addAttribute("searchFallback", true);
            }
        } else {
            // 빈 검색어는 '현재 위치 2km'가 아니라 부산 전체 목록으로 처리한다.
            list = tourismService.allList(lang);
            if (userlat != null && userlon != null) {
                tourismService.allDistence(userlat, userlon, list);
            }
        }

        setMapCenter(model, list, userlat, userlon);

        model.addAttribute("spotname", spotname);
        model.addAttribute("lang", lang);
        model.addAttribute("userlat", userlat);
        model.addAttribute("userlon", userlon);
        model.addAttribute("list", list);
        model.addAttribute("keyword", keyword);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());               // ✅ 추가
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());  // ✅ 추가
        model.addAttribute("currentUserId", SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserId() : null);

        return EXPLORE_VIEW;
    }

    // ─────────────────────────────────────────────────────────
    // 내 위치 기반 추천
    // ─────────────────────────────────────────────────────────
    @GetMapping("/tour/mylocation/{lat}/{lon}")
    public String myLocation(
            @PathVariable Double lat,
            @PathVariable Double lon,
            Model model,
            HttpSession session) {
        String lang = CurrentLanguage.get();
        session.setAttribute("userlat", lat);
        session.setAttribute("userlon", lon);

        // 위치를 받아도 목록 범위를 2km로 축소하지 않는다.
        // 부산 전체 TourAPI 결과를 유지하고 각 장소까지의 거리만 계산한다.
        List<TourPlaceDto> nearbyList = tourismService.allList(lang);
        tourismService.allDistence(lat, lon, nearbyList);

        String spotname = "en".equalsIgnoreCase(lang) ? "My Location" : "내위치";

        model.addAttribute("spotname", spotname);
        model.addAttribute("lang", lang);
        model.addAttribute("list", nearbyList);
        // 현위치를 받아도 지도 중심은 부산을 유지한다.
        model.addAttribute("centerLat", BUSAN_CENTER_LAT);
        model.addAttribute("centerLon", BUSAN_CENTER_LON);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lon);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());               // ✅ 추가
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());  // ✅ 추가
        model.addAttribute("currentUserId", SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserId() : null);

        return EXPLORE_VIEW;
    }

    @GetMapping("/tour/api/detail/{contentId}")
    @ResponseBody
    public Map<String, Object> tourDetail(
            @PathVariable String contentId,
            @RequestParam String contentTypeId,
            @RequestParam(defaultValue = "ko") String lang) {
        return tourismService.getDetail(contentId, contentTypeId, lang);
    }

    // ─────────────────────────────────────────────────────────
    // 최근 검색어 삭제
    // ─────────────────────────────────────────────────────────
    @PostMapping("/tour/search/history/delete")
    @ResponseBody
    public Map<String, Object> deleteKeyword(
            @RequestParam String keyword,
            HttpSession session) {

        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) session.getAttribute("recentKeywords");
        if (keywords != null) {
            keywords.remove(keyword);
            session.setAttribute("recentKeywords", keywords);
        }
        return Map.of("success", true, "keywords", keywords != null ? keywords : List.of());
    }

    @PostMapping("/tour/search/history/clear")
    @ResponseBody
    public Map<String, Object> clearKeywords(HttpSession session) {
        session.removeAttribute("recentKeywords");
        return Map.of("success", true);
    }

    // ─────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void saveKeyword(HttpSession session, String keyword) {
        List<String> keywords = (List<String>) session.getAttribute("recentKeywords");
        if (keywords == null) keywords = new ArrayList<>();

        keywords.remove(keyword);
        keywords.add(0, keyword);
        if (keywords.size() > MAX_RECENT_KEYWORDS)
            keywords = keywords.subList(0, MAX_RECENT_KEYWORDS);

        session.setAttribute("recentKeywords", keywords);
    }

    @SuppressWarnings("unchecked")
    private List<String> getRecentKeywords(HttpSession session) {
        List<String> keywords = (List<String>) session.getAttribute("recentKeywords");
        return keywords != null ? keywords : List.of();
    }

    /** 지도 중심은 검색 결과나 현위치와 무관하게 항상 부산으로 고정한다. */
    private void setMapCenter(Model model, List<TourPlaceDto> list,
                              Double userLat, Double userLon) {
        model.addAttribute("centerLat", BUSAN_CENTER_LAT);
        model.addAttribute("centerLon", BUSAN_CENTER_LON);
    }

}
