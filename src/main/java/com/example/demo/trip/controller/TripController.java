package com.example.demo.trip.controller;

import com.example.demo.trip.dto.PlaceDetailDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.service.TripRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class TripController {

    private final TripRecommendService tripRecommendService;

    // ── 공통 세션 세팅 ──
    private void session(Model model, HttpSession session) {
        Boolean isLoggedIn = (Boolean) session.getAttribute("isLoggedIn");
        String  userName   = (String)  session.getAttribute("currentUserName");
        model.addAttribute("isLoggedIn",       isLoggedIn != null && isLoggedIn);
        model.addAttribute("currentUserName",  userName);
    }

    // ── 여행 플래너 메인 ──
    @GetMapping({"/ko/trip", "/en/trip"})
    public String tripMain(Model model, HttpSession session) {
        session(model, session);
        return "trip/tripMain";
    }

    // ── 추천 결과 ──
    @GetMapping({"/ko/trip/result", "/en/trip/result"})
    public String tripResult(Model model, HttpSession session) {
        session(model, session);
        return "trip/tripResult";
    }

    // ── REST: 지역 목록 (GET /api/trip/areas) ──
    @GetMapping("/api/trip/areas")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> areas() {
        return ResponseEntity.ok(tripRecommendService.getAreaData());
    }

    // ── REST: 추천 요청 (POST /api/trip/recommend) ──
    @PostMapping("/api/trip/recommend")
    @ResponseBody
    public ResponseEntity<TripRecommendResponse> recommend(
            @RequestBody TripRecommendRequest req) {
        return ResponseEntity.ok(tripRecommendService.recommend(req));
    }

    // ── REST: 장소 상세 (GET /api/trip/place/{id}) ──
    @GetMapping("/api/trip/place/{placeId}")
    @ResponseBody
    public ResponseEntity<PlaceDetailDto> placeDetail(@PathVariable Long placeId) {
        PlaceDetailDto detail = tripRecommendService.getPlaceDetail(placeId);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }
}