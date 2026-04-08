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

import java.util.List;

@Controller
public class TransportRouteController {

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private RouteRepository routeRepository;

    @GetMapping("/transport/search")
    public String transportSearch(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String startLat,
            @RequestParam(required = false) String startLng,
            Model model,
            HttpServletRequest request) {
        return handleSearch(start, end, startLat, startLng, model, "transport/transport", request);
    }


    @GetMapping("/ko/transport/search")
    public String transportSearchKo(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String startLat,
            @RequestParam(required = false) String startLng,
            Model model,HttpServletRequest request) {
        return handleSearch(start, end, startLat, startLng, model, "transport/transport", request);
    }

    @GetMapping("/en/transport/search")
    public String transportSearchEn(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String startLat,
            @RequestParam(required = false) String startLng,
            Model model,HttpServletRequest request) {
        return handleSearch(start, end, startLat, startLng, model, "transport/transportEn", request);
    }

    // ✅ 검색 기록 저장
    @ResponseBody
    @PostMapping("/api/transport/history")
    public String saveHistory(@RequestBody Route route,
                              HttpServletRequest request) {

        // 사용자
        String user = SecurityUtil.isLoggedIn()
                ? SecurityUtil.getCurrentUserName()
                : "guest";
        route.setUserId(user);

        // 언어
        String referer = request.getHeader("Referer");
        String lang = (referer != null && referer.contains("/en")) ? "en" : "ko";
        route.setLanguage(lang);

        routeRepository.save(route);
        return "ok";
    }

    private String handleSearch(String start, String end, String startLat, String startLng,
                                Model model, String viewName,
                                HttpServletRequest request) {

        // 로그인 정보
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());

        String user = SecurityUtil.isLoggedIn()
                ? SecurityUtil.getCurrentUserName()
                : "guest";

        String lang = request.getRequestURI().startsWith("/en") ? "en" : "ko";
        model.addAttribute("lang", lang);

        // 검색기록 (한 번만!)
        model.addAttribute("recentRoutes",
                routeRepository.findTop10ByUserIdAndLanguageOrderBySearchedAtDesc(user, lang));

        try {
            List<TransportRouteDto> routes;

            boolean hasCoords = startLat != null && !startLat.isEmpty()
                    && startLng != null && !startLng.isEmpty();

            // 시작점 없으면 검색 안함 (최근검색만 보여줌)
            if ((start == null || start.isEmpty()) && !hasCoords) {
                return viewName;
            }

            // 도착지 없으면 검색 안함
            if (end == null || end.isEmpty()) {
                return viewName;
            }

            if (hasCoords) {
                double lat = Double.parseDouble(startLat);
                double lng = Double.parseDouble(startLng);

                routes = transportRouteService.getTransportRoutesByCoords(lat, lng, end);
                model.addAttribute("start", "현재위치");

            } else {
                routes = transportRouteService.getTransportRoutes(start, end);
                model.addAttribute("start", start);
            }

            // 디버깅 로그
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