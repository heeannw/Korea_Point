package com.example.demo.trip.controller;

import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.trip.dto.PlaceDetailDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.dto.TripReoptimizeRequest;
import com.example.demo.trip.service.TripRecommendService;
import com.example.demo.user.util.SecurityUtil;               // ✅ 추가
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TripController {

    private final TripRecommendService tripRecommendService;
    private final TourApiClient tourApiClient;

    // ── 공통 세션 세팅 (SecurityUtil로 교체) ──
    private void session(Model model, HttpSession session) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());              // ✅ 수정
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName()); // ✅ 수정
    }

    /**
     * 관광/챗봇/홈과 동일하게 세션 로케일(?lang=)을 그대로 반영한다. 예전엔 화면 문구를 전부
     * 한국어 원문 하나로 두고 구글 번역 위젯(trip-translate.js)이 화면에서 즉석 번역했는데,
     * 그 위젯은 이 세션 로케일과 별도로 localStorage에 언어를 저장해서 홈 등에서 언어를 바꿔도
     * trip에는 반영이 안 됐다. 이제 트립도 진짜 메시지 키(#{trip.*})로 서버 렌더링하므로
     * 구글 위젯 전용이던 "zh-CN" 매핑은 더 이상 필요 없다.
     */
    private String currentTripLang() {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        return switch (lang) {
            case "en", "ja", "zh", "fr" -> lang;
            default -> "ko";
        };
    }

    // ── 여행 플래너 메인 (KO) ──
    @GetMapping("/ko/trip")
    public String tripMainKo(Model model, HttpSession session) {
        session(model, session);
        model.addAttribute("lang", currentTripLang());
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
        model.addAttribute("lang", currentTripLang());
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
        model.addAttribute("lang", currentTripLang());
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
     * 관광페이지 상세정보 수준(운영시간·전화번호·메뉴·소개글 등)을 여행일정 상세 패널에도 보여주기 위해
     * tourism의 TourApiClient를 읽기 전용으로 그대로 재사용한다(tourism 파일은 건드리지 않음).
     * contentId가 없는 장소(손으로 넣은 큐레이션 목록)는 애초에 상세 조회 대상이 아니라서
     * 프론트에서 이 API를 호출하지 않는다 — 여기서는 방어적으로만 처리.
     */
    @GetMapping("/api/trip/place/full-detail")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> placeFullDetail(@RequestParam String contentId,
                                                                 @RequestParam(required = false) String contentTypeId) {
        if (contentId == null || contentId.isBlank()) return ResponseEntity.ok(Map.of());
        try {
            return ResponseEntity.ok(tourApiClient.findDetail(contentId, contentTypeId, "ko"));
        } catch (Exception e) {
            log.warn("여행일정 상세정보 조회 실패 (contentId={}): {}", contentId, e.getMessage());
            return ResponseEntity.ok(Map.of());
        }
    }

    /** 상세 패널의 "근처 맛집/근처 카페" — 지금 보고 있는 장소 주변 후보를 가까운 순으로 몇 곳 돌려준다. */
    @GetMapping("/api/trip/place/nearby")
    @ResponseBody
    public ResponseEntity<List<TripRecommendResponse.RecommendedPlace>> placeNearby(
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam String category, @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(tripRecommendService.findNearby(lat, lng, category, Math.min(limit, 10)));
    }
}
