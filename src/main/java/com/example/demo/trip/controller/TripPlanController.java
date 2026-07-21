package com.example.demo.trip.controller;

import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.service.TripPlanService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    /** OAuth2 인증 사용자에게 연결된 DB users.id를 일정 소유자 키로 사용한다. */
    private String currentUserId() {
        if (!SecurityUtil.isLoggedIn()) {
            return null;
        }
        return String.valueOf(SecurityUtil.getCurrentUserId());
    }

    @PostMapping("/save")
    public ResponseEntity<?> save(@RequestBody TripPlanDto.SaveRequest req) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        Long planId = tripPlanService.save(uid, req);
        return ResponseEntity.ok(Map.of("planId", planId, "message", "저장되었습니다"));
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        List<TripPlanDto.Summary> list = tripPlanService.listByUser(uid);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        TripPlanDto.Detail detail = tripPlanService.getDetail(uid, id);
        return detail == null
                ? ResponseEntity.status(404).body(Map.of("error", "계획을 찾을 수 없습니다"))
                : ResponseEntity.ok(detail);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다"));
        boolean ok = tripPlanService.delete(uid, id);
        return ok
                ? ResponseEntity.ok(Map.of("message", "삭제되었습니다"))
                : ResponseEntity.status(404).body(Map.of("error", "계획을 찾을 수 없습니다"));
    }
}
