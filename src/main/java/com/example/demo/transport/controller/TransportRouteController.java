package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.service.TransportRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class TransportRouteController {

    @Autowired
    private TransportRouteService transportRouteService;

    @GetMapping("/transport/search")
    public String transportSearch(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) Double startLat,
            @RequestParam(required = false) Double startLng,
            Model model) {

        // 도착지 없으면 폼만 보여줌
        if (end == null || end.isEmpty()) {
            return "transport/transport";
        }

        // 출발지도 없고 좌표도 없으면 폼만 보여줌
        if ((start == null || start.isEmpty()) && startLat == null) {
            return "transport/transport";
        }

        try {
            List<TransportRouteDto> routes;

            if (startLat != null && startLng != null) {
                // 현재위치(좌표)로 검색
                routes = transportRouteService.getTransportRoutesByCoords(
                        startLat, startLng, end
                );
                model.addAttribute("start", "현재위치");
            } else {
                // 텍스트 입력으로 검색
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

            model.addAttribute("routes", routes);
            model.addAttribute("end", end);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "경로 조회 실패: " + e.getMessage());
        }
        return "transport/transport";
    }
}