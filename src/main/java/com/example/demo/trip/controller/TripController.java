package com.example.demo.trip.controller;

import com.example.demo.trip.dto.*;
import com.example.demo.trip.service.TripRecommendService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class TripController {

    private final TripRecommendService tripRecommendService;

    // ── 여행 추천 메인 페이지 ──
    @GetMapping("/ko/trip")
    public String tripMain(Model model, HttpSession session) {
        Boolean isLoggedIn = (Boolean) session.getAttribute("isLoggedIn");
        String currentUserName = (String) session.getAttribute("currentUserName");
        model.addAttribute("isLoggedIn", isLoggedIn != null && isLoggedIn);
        model.addAttribute("currentUserName", currentUserName);
        return "trip/tripMain";
    }

    // ── 추천 결과 페이지 ──
    @GetMapping("/ko/trip/result")
    public String tripResult(Model model, HttpSession session) {
        Boolean isLoggedIn = (Boolean) session.getAttribute("isLoggedIn");
        String currentUserName = (String) session.getAttribute("currentUserName");
        model.addAttribute("isLoggedIn", isLoggedIn != null && isLoggedIn);
        model.addAttribute("currentUserName", currentUserName);
        return "trip/tripResult";
    }

    // ── REST: 추천 요청 ──
    @PostMapping("/api/trip/recommend")
    @ResponseBody
    public ResponseEntity<TripRecommendResponse> recommend(
            @RequestBody TripRecommendRequest req) {
        TripRecommendResponse result = tripRecommendService.recommend(req);
        return ResponseEntity.ok(result);
    }

    // ── REST: 장소 상세 정보 ──
    @GetMapping("/api/trip/place/{placeId}")
    @ResponseBody
    public ResponseEntity<PlaceDetailDto> placeDetail(@PathVariable Long placeId) {
        PlaceDetailDto detail = tripRecommendService.getPlaceDetail(placeId);
        if (detail == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(detail);
    }
}