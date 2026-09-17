package com.example.demo.trip.controller;

import com.example.demo.config.LocalizedMessages;
import com.example.demo.trip.service.TripDisplayText;
import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.dto.TripRecommendResponse;
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
    private final TripDisplayText displayText;
    private final LocalizedMessages messages;

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
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        Long planId = tripPlanService.save(uid, req);
        return ResponseEntity.ok(Map.of("planId", planId, "message", messages.get("trip.api.saved")));
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        List<TripPlanDto.Summary> list = tripPlanService.listByUser(uid);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        TripPlanDto.Detail detail = tripPlanService.getDetail(uid, id);
        return detail == null
                ? ResponseEntity.status(404).body(Map.of("error", messages.get("trip.api.notFound")))
                : ResponseEntity.ok(displayText.apply(detail));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        boolean ok = tripPlanService.delete(uid, id);
        return ok
                ? ResponseEntity.ok(Map.of("message", messages.get("trip.api.deleted")))
                : ResponseEntity.status(404).body(Map.of("error", messages.get("trip.api.notFound")));
    }

    @PatchMapping("/{id}/title")
    public ResponseEntity<?> updateTitle(@PathVariable Long id,
                                         @RequestBody TripPlanDto.TitleUpdateRequest request) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        boolean ok = tripPlanService.updateTitle(uid, id, request.getTitle());
        return ok
                ? ResponseEntity.ok(Map.of("message", messages.get("trip.api.updated")))
                : ResponseEntity.status(404).body(Map.of("error", messages.get("trip.api.notFound")));
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<?> copy(@PathVariable Long id) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        Long copiedPlanId = tripPlanService.copy(uid, id);
        return ResponseEntity.ok(Map.of("planId", copiedPlanId, "message", messages.get("trip.api.copied")));
    }

    /** "상세 페이지에서 보기" 전용: 저장된 일정 + 그 조건으로 다시 계산한 추천 리스트/슬롯별 대체 후보. */
    @GetMapping("/{id}/for-result-view")
    public ResponseEntity<?> forResultView(@PathVariable Long id) {
        String uid = currentUserId();
        if (uid == null) return ResponseEntity.status(401).body(Map.of("error", messages.get("trip.api.loginRequired")));
        TripRecommendResponse resp = tripPlanService.buildResultView(uid, id);
        return resp == null
                ? ResponseEntity.status(404).body(Map.of("error", messages.get("trip.api.notFound")))
                : ResponseEntity.ok(displayText.apply(resp));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", messages.of(exception)));
    }
}
