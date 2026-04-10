package com.example.demo.trip.controller;

import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.service.TripPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * 여행 계획 저장 / 목록 / 상세 / 삭제
 *
 * POST   /api/trip/plan/save   → 저장
 * GET    /api/trip/plan/list   → 내 목록
 * GET    /api/trip/plan/{id}   → 상세
 * DELETE /api/trip/plan/{id}   → 삭제
 */
@RestController
@RequestMapping("/api/trip/plan")
@RequiredArgsConstructor
public class TripPlanController {

    private final TripPlanService tripPlanService;

    /** 세션에서 userId 추출 — 프로젝트의 실제 키에 맞게 확인 필요 */
    private String userId(HttpSession session) {
        String id = (String) session.getAttribute("userId");
        if (id == null) id = (String) session.getAttribute("currentUserName");
        return id;
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody TripPlanDto.SaveRequest req,
                                  HttpSession session) {
        String uid = userId(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        Long planId = tripPlanService.save(uid, req);
        return ResponseEntity.ok(Map.of("planId", planId, "message", "저장되었습니다"));
    }

    @GetMapping("/list")
    public ResponseEntity<?> list(HttpSession session) {
        String uid = userId(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        List<TripPlanDto.Summary> list = tripPlanService.listByUser(uid);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, HttpSession session) {
        String uid = userId(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        TripPlanDto.Detail detail = tripPlanService.getDetail(uid, id);
        return detail == null
                ? ResponseEntity.status(404).body(Map.of("error", "계획을 찾을 수 없습니다"))
                : ResponseEntity.ok(detail);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpSession session) {
        String uid = userId(session);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        boolean ok = tripPlanService.delete(uid, id);
        return ok
                ? ResponseEntity.ok(Map.of("message", "삭제되었습니다"))
                : ResponseEntity.status(404).body(Map.of("error", "계획을 찾을 수 없습니다"));
    }
}