package com.example.demo.trip.controller;

import com.example.demo.trip.ai.GeminiTripSummaryService;
import com.example.demo.trip.dto.PlaceDetailDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.dto.TripReoptimizeRequest;
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
    private final GeminiTripSummaryService geminiTripSummaryService;

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
    public String tripMainEn() {
        return "redirect:/ko/trip";
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
    public String tripResultEn() {
        return "redirect:/ko/trip/result";
    }

    @GetMapping("/ko/trip/plans")
    public String tripPlansKo(Model model, HttpSession session) {
        session(model, session);
        model.addAttribute("lang", "ko");
        return "trip/tripPlanList";
    }

    @GetMapping("/en/trip/plans")
    public String tripPlansEn() {
        return "redirect:/ko/trip/plans";
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

    @PostMapping("/api/trip/reoptimize")
    @ResponseBody
    public ResponseEntity<TripRecommendResponse> reoptimize(@RequestBody TripReoptimizeRequest request) {
        return ResponseEntity.ok(tripRecommendService.reoptimize(request));
    }

    // ── REST: 장소 상세 (GET /api/trip/place/{id}) ──
    @GetMapping("/api/trip/place/{placeId}")
    @ResponseBody
    public ResponseEntity<PlaceDetailDto> placeDetail(@PathVariable Long placeId) {
        PlaceDetailDto detail = tripRecommendService.getPlaceDetail(placeId);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }

    /**
     * 장소 상세 패널의 "이 장소는 이런 곳이에요" 한 줄 개요.
     * 큐레이션 장소든 TourAPI 장소든 원문 소개글이 다 있는 게 아니라서 Gemini에게 즉석에서 물어본다.
     */
    @GetMapping("/api/trip/place/description")
    @ResponseBody
    public ResponseEntity<Map<String, String>> placeDescription(@RequestParam String name,
                                                                  @RequestParam(required = false) String address) {
        String description = geminiTripSummaryService.describePlace(name, address);
        return ResponseEntity.ok(Map.of("description", description));
    }
}
