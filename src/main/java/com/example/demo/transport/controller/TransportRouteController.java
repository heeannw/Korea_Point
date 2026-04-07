package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.TransportRouteService;
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
    public String transportSearchRedirect() {
        return "redirect:/ko/transport/search";
    }

    @GetMapping("/ko/transport/search")
    public String transportSearchKo(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String startLat,
            @RequestParam(required = false) String startLng,
            Model model) {
        return handleSearch(start, end, startLat, startLng, model, "transport/transport");
    }

    @GetMapping("/en/transport/search")
    public String transportSearchEn(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String startLat,
            @RequestParam(required = false) String startLng,
            Model model) {
        return handleSearch(start, end, startLat, startLng, model, "transport/transportEn");
    }

    // 경로 클릭 시 저장 API
    @ResponseBody
    @PostMapping("/api/transport/history")
    public String saveHistory(@RequestBody Route route) {
        routeRepository.save(route);
        return "ok";
    }

    private String handleSearch(String start, String end, String startLat, String startLng,
                                Model model, String viewName) {
        if (end == null || end.isEmpty()) {
            model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());
            return viewName;
        }

        if ((start == null || start.isEmpty()) && (startLat == null || startLat.isEmpty())) {
            model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());
            return viewName;
        }

        try {
            List<TransportRouteDto> routes;
            boolean hasCoords = startLat != null && !startLat.isEmpty()
                    && startLng != null && !startLng.isEmpty();

            if (hasCoords) {
                double lat = Double.parseDouble(startLat);
                double lng = Double.parseDouble(startLng);
                routes = transportRouteService.getTransportRoutesByCoords(lat, lng, end);
                model.addAttribute("start", "현재위치");
                start = "현재위치";
            } else {
                if ("현재위치".equals(start)) {
                    model.addAttribute("error", "현재위치 좌표를 가져올 수 없습니다. 다시 시도해주세요.");
                    model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());
                    return viewName;
                }
                routes = transportRouteService.getTransportRoutes(start, end);
                model.addAttribute("start", start);
            }

            System.out.println("=== 경로 조회 결과 ===");
            System.out.println("총 경로 수: " + routes.size());
            for (TransportRouteDto r : routes) {
                System.out.println("타입: " + r.getType()
                        + " | 시간: " + r.getTotalTime()
                        + "분 | 환승: " + r.getTransferCount()
                        + "회 | routeId: " + r.getRouteId());
            }
            System.out.println("====================");

            // 검색 시점엔 저장하지 않음 - 클릭할 때 저장
            model.addAttribute("routes", routes);
            model.addAttribute("end", end);
            model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "경로 조회 실패: " + e.getMessage());
        }
        return viewName;
    }
}