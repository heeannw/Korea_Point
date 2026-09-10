package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.TransportRouteService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Controller
public class TransportRouteController {

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private RouteRepository routeRepository;

    @ResponseBody
    @GetMapping(value = "/api/kakao/search", produces = "application/json")
    public String kakaoSearch(@RequestParam String query,
                              @RequestParam(required = false) String category,
                              @RequestParam(defaultValue = "4") int size) throws Exception {
        return transportRouteService.searchKakaoPlaces(query, category, size);
    }

    @ResponseBody
    @GetMapping(value = "/api/kakao/reverse", produces = "application/json")
    public String kakaoReverse(@RequestParam double x, @RequestParam double y) throws Exception {
        return transportRouteService.reverseGeocode(x, y);
    }

    @GetMapping("/transport/search")
    public String transportSearchRedirect(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String endLat,
            @RequestParam(required = false) String endLng) {
        StringBuilder sb = new StringBuilder("redirect:/ko/transport/search");
        List<String> params = new ArrayList<>();
        try {
            if (start    != null && !start.isEmpty())    params.add("start="    + URLEncoder.encode(start,    StandardCharsets.UTF_8));
            if (end      != null && !end.isEmpty())      params.add("end="      + URLEncoder.encode(end,      StandardCharsets.UTF_8));
            if (endLat   != null && !endLat.isEmpty())   params.add("endLat="   + URLEncoder.encode(endLat,   StandardCharsets.UTF_8));
            if (endLng   != null && !endLng.isEmpty())   params.add("endLng="   + URLEncoder.encode(endLng,   StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 인코딩 실패 시 /ko/transport/search로 이동
        }
        if (!params.isEmpty()) sb.append("?").append(String.join("&", params));
        return sb.toString();
    }

    @GetMapping("/ko/transport/search")
    public String transportSearchKo(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String endLat,
            @RequestParam(required = false) String endLng,
            Model model) {
        return handleSearch(start, end, model, "transport/transport");
    }

    @GetMapping("/en/transport/search")
    public String transportSearchEn(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String endLat,
            @RequestParam(required = false) String endLng,
            Model model) {
        return handleSearch(start, end, model, "transport/transportEn");
    }

    // 검색 기록 저장
    @ResponseBody
    @PostMapping("/api/transport/history")
    public String saveHistory(@RequestBody Route route,
                              HttpServletRequest request) {
        String user = SecurityUtil.getCurrentUserName();
        route.setUserId(user);

        String referer = request.getHeader("Referer");
        String lang = (referer != null && referer.contains("/en")) ? "en" : "ko";
        route.setLanguage(lang);

        routeRepository.save(route);
        return "ok";
    }

    private String handleSearch(String start, String end, Model model, String viewName) {

        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());

        String user = SecurityUtil.getCurrentUserName();

        String lang = viewName.contains("En") ? "en" : "ko";
        model.addAttribute("lang", lang);

        model.addAttribute("recentRoutes", SecurityUtil.isLoggedIn()
                ? routeRepository.findTop10ByUserIdAndLanguageOrderBySearchedAtDesc(user, lang)
                : java.util.List.of());

        try {
            List<TransportRouteDto> routes;

            // 시작점 없으면 검색 안 함 (최근 검색만 표시)
            if (start == null || start.isEmpty()) {
                if (end != null && !end.isEmpty()) model.addAttribute("end", end);
                return viewName;
            }

            // 도착지 없으면 검색 안 함
            if (end == null || end.isEmpty()) {
                return viewName;
            }

            routes = transportRouteService.getTransportRoutes(start, end);
            model.addAttribute("start", start);

            System.out.println("=== 경로 조회 결과 ===");
            System.out.println("총 경로 수: " + routes.size());
            for (TransportRouteDto r : routes) {
                System.out.println("타입: " + r.getType()
                        + " | 시간: " + r.getTotalTime()
                        + "분 | 환승: " + r.getTransferCount()
                        + "회 | routeId: " + r.getRouteId());
            }
            System.out.println("====================");

            model.addAttribute("routes", routes);
            model.addAttribute("end", end);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "경로 조회 실패: " + e.getMessage());
        }

        return viewName;
    }
}
