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
    public String transportSearch(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String startLat,  // ✅ Double → String
            @RequestParam(required = false) String startLng,  // ✅ Double → String
            Model model) {

        if (end == null || end.isEmpty()) {
            model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());
            return "transport/transport";
        }

        if ((start == null || start.isEmpty()) && (startLat == null || startLat.isEmpty())) {
            model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());
            return "transport/transport";
        }

        try {
            List<TransportRouteDto> routes;

            // ✅ 좌표가 실제로 있는지 확인
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
                    return "transport/transport";
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

            // 검색 기록 저장
            if (!routes.isEmpty()) {
                for (TransportRouteDto r : routes) {
                    Route route = new Route();
                    route.setStartPlace(start);
                    route.setEndPlace(end);
                    route.setTotalTime(r.getTotalTime());
                    route.setTransferCount(r.getTransferCount());
                    route.setRouteType(r.getType());
                    routeRepository.save(route);
                }
            }

            model.addAttribute("routes", routes);
            model.addAttribute("end", end);
            model.addAttribute("recentRoutes", routeRepository.findTop10ByOrderBySearchedAtDesc());

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "경로 조회 실패: " + e.getMessage());
        }
        return "transport/transport";
    }
}