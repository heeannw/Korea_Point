package com.example.demo.tourism.controller;

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
                            @RequestParam(value = "lang") String lang
    ) {
        List<TourPlaceDto> displayList = tourismService.allList(lang);
        displayList = displayList.stream().limit(100).toList();
        session.setAttribute("userlat", lat);
        session.setAttribute("userlon", lon);

        List<TourPlaceDto> dlist = (lat != null && lon != null)
                ? tourismService.allDistence(lat, lon, displayList)
                : displayList;

        String spotname = "en".equalsIgnoreCase(lang) ? "My Location" : "내위치";

        model.addAttribute("spotname", spotname);
        model.addAttribute("centerLat", 37.5665);
        model.addAttribute("centerLon", 126.9780);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lon);
        model.addAttribute("lang", lang);
        model.addAttribute("list", dlist);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());               // ✅ 추가
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());  // ✅ 추가

        return "en".equalsIgnoreCase(lang) ? "tourism/tourismEN" : "tourism/tourismKo";
    }

    // ─────────────────────────────────────────────────────────
    // 키워드 검색 + 내 위치까지 거리 표시
    // ─────────────────────────────────────────────────────────
    @RequestMapping("/tour/search/")
    public String tourSearch(
            @RequestParam(required = false) String keyword,
            Model model,
            @RequestParam("lang") String lang,
            @RequestParam(value = "spotname", required = false) String spotname,
            HttpSession session) {

        if (spotname == null || spotname.isBlank()) {
            spotname = "en".equalsIgnoreCase(lang) ? "My Location" : "내위치";
        }

        Double userlat = (Double) session.getAttribute("userlat");
        Double userlon = (Double) session.getAttribute("userlon");
        System.out.println("keyword : !!!!!!!!!!!!!!>>>>" + keyword);
        List<TourPlaceDto> list;

        if (keyword != null && !keyword.isBlank()) {
            saveKeyword(session, keyword.trim());
            list = tourismService.searchKeyword(keyword.trim(), userlat, userlon, lang);

            if (list.isEmpty()) {
                double fallbackLat = userlat != null ? userlat : 37.5665;
                double fallbackLon = userlon != null ? userlon : 126.9780;
                list = tourismService.analyzeLocation(fallbackLat, fallbackLon, lang);
                model.addAttribute("searchFallback", true);
            }
        } else {
            double fallbackLat = userlat != null ? userlat : 37.5665;
            double fallbackLon = userlon != null ? userlon : 126.9780;
            list = tourismService.analyzeLocation(fallbackLat, fallbackLon, lang);
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

        return "en".equalsIgnoreCase(lang) ? "tourism/tourismEN" : "tourism/tourismKo";
    }

    // ─────────────────────────────────────────────────────────
    // 내 위치 기반 추천
    // ─────────────────────────────────────────────────────────
    @GetMapping("/tour/mylocation/{lat}/{lon}")
    public String myLocation(
            @PathVariable Double lat,
            @PathVariable Double lon,
            @RequestParam("lang") String lang,
            Model model,
            HttpSession session) {

        session.setAttribute("userlat", lat);
        session.setAttribute("userlon", lon);

        List<TourPlaceDto> nearbyList = tourismService.analyzeLocation(lat, lon, lang);

        String spotname = "en".equalsIgnoreCase(lang) ? "My Location" : "내위치";

        model.addAttribute("spotname", spotname);
        model.addAttribute("lang", lang);
        model.addAttribute("list", nearbyList);
        model.addAttribute("centerLat", lat);
        model.addAttribute("centerLon", lon);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lon);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());               // ✅ 추가
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());  // ✅ 추가

        return "en".equalsIgnoreCase(lang) ? "tourism/tourismEN" : "tourism/tourismKo";
    }

    // ─────────────────────────────────────────────────────────
    // Save to Itinerary → 근처 카페/음식점 페이지
    // ─────────────────────────────────────────────────────────
    @GetMapping("/tour/itinerary")
    public String itinerary(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam("lang") String lang,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1.0") double radius,
            Model model,
            HttpSession session) {

        List<TourPlaceDto> foodPlaces = tourismService.getNearbyFoodPlaces(lat, lon, radius, lang);

        model.addAttribute("placeLat", lat);
        model.addAttribute("placeLon", lon);
        model.addAttribute("placeTitle", title);
        model.addAttribute("foodPlaces", foodPlaces);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("radius", radius);

        Double userlat = (Double) session.getAttribute("userlat");
        Double userlon = (Double) session.getAttribute("userlon");
        model.addAttribute("userlat", userlat);
        model.addAttribute("userlon", userlon);

        return "tourism/itinerary";
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

    private void setMapCenter(Model model, List<TourPlaceDto> list,
                              Double userLat, Double userLon) {
        if (list.isEmpty()) {
            model.addAttribute("centerLat", userLat != null ? userLat : 37.5665);
            model.addAttribute("centerLon", userLon != null ? userLon : 126.9780);
            return;
        }
        int count = Math.min(5, list.size());
        double latSum = 0, lonSum = 0;
        for (int i = 0; i < count; i++) {
            latSum += list.get(i).getLat();
            lonSum += list.get(i).getLng();
        }
        model.addAttribute("centerLat", latSum / count);
        model.addAttribute("centerLon", lonSum / count);
    }

    @RequestMapping("/tour/location")
    public String locationOK() {
        return "tourism/tourLocation";
    }

    @RequestMapping("tour/gotransport")
    public String goTransport(@RequestParam("userlat") Double userlat,
                              @RequestParam("userlon") Double userlon,
                              @RequestParam("title") String title,
                              @RequestParam("lat") Double placeLat,
                              @RequestParam("lon") Double placeLon,
                              @RequestParam(value = "lang", defaultValue = "KR") String lang) {
        String encoded = java.net.URLEncoder.encode(title, java.nio.charset.StandardCharsets.UTF_8);
        String base = "en".equalsIgnoreCase(lang) ? "/en/transport/search" : "/ko/transport/search";
        return "redirect:" + base
                + "?startLat=" + userlat
                + "&startLng=" + userlon
                + "&end=" + encoded;
    }
}