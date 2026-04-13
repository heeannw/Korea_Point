package com.example.demo.trip.controller;

import com.example.demo.trip.dto.PlaceDetailDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.service.TripRecommendService;
import com.example.demo.user.util.SecurityUtil;               // ✅ 추가
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

    // ── 공통 세션 세팅 (SecurityUtil로 교체) ──
    private void session(Model model, HttpSession session) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());              // ✅ 수정
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName()); // ✅ 수정
    }

    // ── 여행 플래너 메인 (KO) ──
    @GetMapping("/ko/trip")
    public String tripMainKo(Model model, HttpSession session) {
        session(model, session);
        model.addAttribute("lang", "ko");
        return "trip/tripMain";
    }

    // ── 여행 플래너 메인 (EN) ──
    @GetMapping("/en/trip")
    public String tripMainEn(Model model, HttpSession session) {
        session(model, session);
        model.addAttribute("lang", "en");
        return "trip/tripMainEn";
    }

    // ── 추천 결과 (KO) ──
    @GetMapping("/ko/trip/result")
    public String tripResultKo(Model model, HttpSession session) {
        session(model, session);
        model.addAttribute("lang", "ko");
        return "trip/tripResult";
    }

    // ── 추천 결과 (EN) ──
    @GetMapping("/en/trip/result")
    public String tripResultEn(Model model, HttpSession session) {
        session(model, session);
        model.addAttribute("lang", "en");
        return "trip/tripResultEn";
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