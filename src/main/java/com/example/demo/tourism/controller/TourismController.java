package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourismService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class TourismController {

    private static final int MAX_RECENT_KEYWORDS = 10;
    private static final double DEFAULT_LAT = 37.5665;
    private static final double DEFAULT_LON = 126.9780;

    @Autowired
    TourismService tourismService;

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;

    @RequestMapping("/tour/all")
    public String tourAll(Model model, HttpSession session) {
        Double userLat = (Double) session.getAttribute("userlat");
        Double userLon = (Double) session.getAttribute("userlon");
        boolean hasStoredLocation = userLat != null && userLon != null;

        if (hasStoredLocation) {
            List<TourPlaceDto> nearbyList = tourismService.analyzeLocation(userLat, userLon);
            model.addAttribute("list", nearbyList);
            model.addAttribute("centerLat", userLat);
            model.addAttribute("centerLon", userLon);
            model.addAttribute("userlat", userLat);
            model.addAttribute("userlon", userLon);
        } else {
            List<TourPlaceDto> defaultList = tourismService.analyzeLocation(DEFAULT_LAT, DEFAULT_LON);
            model.addAttribute("list", defaultList);
            model.addAttribute("centerLat", DEFAULT_LAT);
            model.addAttribute("centerLon", DEFAULT_LON);
        }

        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("needsLocationRefresh", !hasStoredLocation);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        return "tourism/explore";
    }

    @RequestMapping("/tour/search/")
    public String tourSearch(
            @RequestParam(required = false) String keyword,
            Model model,
            HttpSession session
    ) {
        Double userLat = (Double) session.getAttribute("userlat");
        Double userLon = (Double) session.getAttribute("userlon");

        List<TourPlaceDto> list;
        if (keyword != null && !keyword.isBlank()) {
            saveKeyword(session, keyword.trim());
            list = tourismService.searchKeyword(keyword.trim(), userLat, userLon);

            if (list.isEmpty()) {
                double fallbackLat = userLat != null ? userLat : DEFAULT_LAT;
                double fallbackLon = userLon != null ? userLon : DEFAULT_LON;
                list = tourismService.analyzeLocation(fallbackLat, fallbackLon);
                model.addAttribute("searchFallback", true);
            }
        } else {
            double fallbackLat = userLat != null ? userLat : DEFAULT_LAT;
            double fallbackLon = userLon != null ? userLon : DEFAULT_LON;
            list = tourismService.analyzeLocation(fallbackLat, fallbackLon);
        }

        setMapCenter(model, list, userLat, userLon);
        model.addAttribute("userlat", userLat);
        model.addAttribute("userlon", userLon);
        model.addAttribute("list", list);
        model.addAttribute("keyword", keyword);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("needsLocationRefresh", false);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        return "tourism/explore";
    }

    @GetMapping("/tour/mylocation/{lat}/{lon}")
    public String myLocation(
            @PathVariable Double lat,
            @PathVariable Double lon,
            Model model,
            HttpSession session
    ) {
        session.setAttribute("userlat", lat);
        session.setAttribute("userlon", lon);

        List<TourPlaceDto> nearbyList = tourismService.analyzeLocation(lat, lon);
        model.addAttribute("list", nearbyList);
        model.addAttribute("centerLat", lat);
        model.addAttribute("centerLon", lon);
        model.addAttribute("userlat", lat);
        model.addAttribute("userlon", lon);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("needsLocationRefresh", false);
        model.addAttribute("recentKeywords", getRecentKeywords(session));
        return "tourism/explore";
    }

    @GetMapping("/tour/itinerary")
    public String itinerary(
            @RequestParam Double lat,
            @RequestParam Double lon,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1.0") double radius,
            Model model,
            HttpSession session
    ) {
        List<TourPlaceDto> foodPlaces = tourismService.getNearbyFoodPlaces(lat, lon, radius);

        model.addAttribute("placeLat", lat);
        model.addAttribute("placeLon", lon);
        model.addAttribute("placeTitle", title);
        model.addAttribute("foodPlaces", foodPlaces);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("radius", radius);
        model.addAttribute("userlat", session.getAttribute("userlat"));
        model.addAttribute("userlon", session.getAttribute("userlon"));
        return "tourism/itinerary";
    }

    @PostMapping("/tour/search/history/delete")
    @ResponseBody
    public Map<String, Object> deleteKeyword(
            @RequestParam String keyword,
            HttpSession session
    ) {
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

    @SuppressWarnings("unchecked")
    private void saveKeyword(HttpSession session, String keyword) {
        List<String> keywords = (List<String>) session.getAttribute("recentKeywords");
        if (keywords == null) {
            keywords = new ArrayList<>();
        }

        keywords.remove(keyword);
        keywords.add(0, keyword);
        if (keywords.size() > MAX_RECENT_KEYWORDS) {
            keywords = keywords.subList(0, MAX_RECENT_KEYWORDS);
        }

        session.setAttribute("recentKeywords", keywords);
    }

    @SuppressWarnings("unchecked")
    private List<String> getRecentKeywords(HttpSession session) {
        List<String> keywords = (List<String>) session.getAttribute("recentKeywords");
        return keywords != null ? keywords : List.of();
    }

    private void setMapCenter(Model model, List<TourPlaceDto> list, Double userLat, Double userLon) {
        if (list.isEmpty()) {
            model.addAttribute("centerLat", userLat != null ? userLat : DEFAULT_LAT);
            model.addAttribute("centerLon", userLon != null ? userLon : DEFAULT_LON);
            return;
        }

        int count = Math.min(5, list.size());
        double latSum = 0;
        double lonSum = 0;
        for (int i = 0; i < count; i++) {
            latSum += list.get(i).getLat();
            lonSum += list.get(i).getLng();
        }

        model.addAttribute("centerLat", latSum / count);
        model.addAttribute("centerLon", lonSum / count);
    }
}
