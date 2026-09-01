package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.SavedPlaceDto;
import com.example.demo.tourism.service.SavedPlaceService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 저장(찜) 장소 API. {@code /tour/**} 이라 SecurityConfig에서 이미 인증 필수다.
 */
@RestController
@RequestMapping("/tour/api/saved-places")
@RequiredArgsConstructor
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    /** 저장 목록. kind(tourism|food|cafe)로 필터 가능. */
    @GetMapping
    public List<SavedPlaceDto> list(@RequestParam(required = false) String kind) {
        return savedPlaceService.list(SecurityUtil.getCurrentUserId(), kind);
    }

    /** 저장 버튼 상태 표시용 — 저장한 contentId 목록만. */
    @GetMapping("/ids")
    public List<String> ids() {
        return savedPlaceService.list(SecurityUtil.getCurrentUserId(), null)
                .stream().map(SavedPlaceDto::contentId).toList();
    }

    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of("count", savedPlaceService.count(SecurityUtil.getCurrentUserId()));
    }

    /** body: { "contentId": "...", "contentTypeId": "39" }. 이미 있으면 그 행을 돌려준다. */
    @PostMapping
    public SavedPlaceDto save(@RequestBody Map<String, String> body) {
        return savedPlaceService.save(
                SecurityUtil.getCurrentUserId(),
                body.get("contentId"),
                body.get("contentTypeId"));
    }

    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> remove(@PathVariable String contentId) {
        savedPlaceService.remove(SecurityUtil.getCurrentUserId(), contentId);
        return ResponseEntity.noContent().build();
    }
}
