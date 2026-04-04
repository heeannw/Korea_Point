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

        // 파라미터 없으면 검색 폼만 보여줌
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) {
            return "transport/transport";
        }

        try {
            List<TransportRouteDto> routes =
                    transportRouteService.getTransportRoutes(start, end);
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