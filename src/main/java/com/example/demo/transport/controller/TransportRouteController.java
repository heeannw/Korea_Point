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
            Model model) {

        if (start == null || end == null || start.isEmpty() || end.isEmpty()) {
            return "transport/transport";
        }

        try {
            List<TransportRouteDto> routes =
                    transportRouteService.getTransportRoutes(start, end);

            // ODsay 경로 개수 확인
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
            model.addAttribute("start", start);
            model.addAttribute("end", end);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "경로 조회 실패: " + e.getMessage());
        }
        return "transport/transport";
    }
}