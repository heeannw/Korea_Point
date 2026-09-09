package com.example.demo.tourism.controller;

import com.example.demo.tourism.ai.GeminiRecoReasonService;
import com.example.demo.tourism.config.RelatedAttractionData;
import com.example.demo.tourism.config.RelatedAttractionData.Related;
import com.example.demo.tourism.dto.PersonalRecoDto;
import com.example.demo.tourism.dto.UserTasteProfile;
import com.example.demo.tourism.entity.UserTourPref;
import com.example.demo.tourism.repository.UserTourPrefRepository;
import com.example.demo.tourism.service.ActivityLogService;
import com.example.demo.tourism.service.ActivityLogService.ActivityRequest;
import com.example.demo.tourism.service.PersonalRecommendService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 관광 개인화 추천 API. {@code /tour/**} 라 로그인 필수는 SecurityConfig 에서 보장됨.
 *  - POST /tour/api/activity   행동 로그 1건
 *  - GET/POST /tour/api/tour-pref  콜드스타트 선호 카테고리
 *  - GET  /tour/api/reco/for-you   (PersonalRecommendService, 다음 단계에서 연결)
 */
@RestController
@RequestMapping("/tour/api")
@RequiredArgsConstructor
public class PersonalRecommendController {

    /** 온보딩 선호 카테고리 = 탐색 화면 칩과 같은 어휘로 통일. */
    private static final List<String> PREF_SLUGS = List.of("attraction", "culture", "leisure", "shopping", "festival");

    private final ActivityLogService activityLogService;
    private final UserTourPrefRepository tourPrefRepository;
    private final PersonalRecommendService recommendService;
    private final GeminiRecoReasonService reasonService;
    private final RelatedAttractionData relatedData;

    /** 연관 관광지 (다른 여행자 방문 동선 기반). 프론트가 PLACES 와 조인해 렌더. 없으면 빈 배열. */
    @GetMapping("/related/{contentId}")
    public List<Related> related(@PathVariable String contentId) {
        return relatedData.forContent(contentId);
    }

    /** 나를 위한 추천 TOP 5 (규칙 엔진 선정 + Gemini 근거 문장). 신호 없으면 빈 배열. */
    @GetMapping("/reco/for-you")
    public List<PersonalRecoDto> forYou(@RequestParam(defaultValue = "ko") String lang) {
        Long userId = SecurityUtil.getCurrentUserId();
        UserTasteProfile profile = recommendService.buildProfile(userId);
        List<PersonalRecoDto> recos = recommendService.recommend(userId, lang);
        return reasonService.explain(profile, recos);
    }

    @PostMapping("/activity")
    public ResponseEntity<Void> logActivity(@RequestBody ActivityRequest req) {
        if (SecurityUtil.isLoggedIn()) {
            activityLogService.record(SecurityUtil.getCurrentUserId(), req);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tour-pref")
    public Map<String, List<String>> getPref() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<String> cats = tourPrefRepository.findById(userId)
                .map(p -> splitCsv(p.getCategories()))
                .orElseGet(List::of);
        return Map.of("categories", cats);
    }

    @PostMapping("/tour-pref")
    public ResponseEntity<Void> savePref(@RequestBody Map<String, List<String>> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<String> cats = body.getOrDefault("categories", List.of()).stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(PREF_SLUGS::contains)
                .distinct()
                .limit(3)
                .toList();
        UserTourPref pref = tourPrefRepository.findById(userId).orElseGet(UserTourPref::new);
        pref.setUserId(userId);
        pref.setCategories(String.join(",", cats));
        tourPrefRepository.save(pref);
        return ResponseEntity.noContent().build();
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
