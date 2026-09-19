package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.PersonalRecoDto;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.dto.UserTasteProfile;
import com.example.demo.tourism.entity.ActivityType;
import com.example.demo.tourism.entity.UserActivity;
import com.example.demo.tourism.repository.UserActivityRepository;
import com.example.demo.tourism.repository.UserTourPrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 관광 개인화 추천 엔진.
 *
 * <p>행동 로그 → 취향 프로필(카테고리·지역·소분류 가중치) → 후보 스코어링 → TOP 5.
 * 후보 풀은 새 DB 가 아니라 {@link TourismService#allList} 가 주는 부산 전체 목록(이미 캐시됨).
 * Gemini 는 여기서 부르지 않는다 — 선정은 이 규칙 엔진이 하고, 문장 설명만
 * {@code GeminiRecoReasonService} 가 붙인다.</p>
 */
@Service
@RequiredArgsConstructor
public class PersonalRecommendService {

    private final UserActivityRepository activityRepository;
    private final UserTourPrefRepository tourPrefRepository;
    private final TourismService tourismService;
    private final RecoReasonText reasonText;

    private static final Map<ActivityType, Double> ACTION_WEIGHT = Map.of(
            ActivityType.REVIEW, 6.0,
            ActivityType.SAVE, 5.0,
            ActivityType.DETAIL_VIEW, 3.0,
            ActivityType.CATEGORY, 2.0,
            ActivityType.SEARCH, 2.0);
    private static final int RECENT_N = 60;
    private static final int TOP_N = 5;

    private static final Set<String> BUSAN_GU = Set.of(
            "중구", "서구", "동구", "영도구", "부산진구", "동래구", "남구", "북구", "해운대구",
            "사하구", "금정구", "강서구", "연제구", "수영구", "사상구", "기장군");

    @Value("${tourism.reco.w-type:0.40}")     private double wType;
    @Value("${tourism.reco.w-area:0.30}")     private double wArea;
    @Value("${tourism.reco.w-detail:0.20}")   private double wDetail;
    @Value("${tourism.reco.w-recency:0.10}")  private double wRecency;
    @Value("${tourism.reco.seen-penalty:0.35}") private double seenPenalty;

    // ── 프로필 ──────────────────────────────────────────────

    public UserTasteProfile buildProfile(Long userId) {
        List<UserActivity> acts = activityRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, RECENT_N));

        Map<String, Double> type = new HashMap<>();
        Map<String, Double> area = new HashMap<>();
        Map<String, Double> detail = new HashMap<>();
        Set<String> seen = new HashSet<>();
        String recentArea = null, recentDetail = null;

        int n = Math.max(1, acts.size());
        for (int i = 0; i < acts.size(); i++) {
            UserActivity a = acts.get(i);
            double decay = 1.0 - 0.4 * i / n;   // 최신 1.0 → 가장 오래된 것 ≈ 0.6
            double w = ACTION_WEIGHT.getOrDefault(a.getAction(), 1.0) * decay;

            if (a.getContentId() != null) seen.add(a.getContentId());

            String slug = a.getAction() == ActivityType.CATEGORY
                    ? a.getCategorySlug()
                    : slugOfType(a.getContentTypeId());
            if (slug != null) type.merge(slug, w, Double::sum);

            if (a.getArea() != null) {
                area.merge(a.getArea(), w, Double::sum);
                if (recentArea == null) recentArea = a.getArea();
            }
            if (a.getAction() != ActivityType.CATEGORY && a.getCategorySlug() != null) {
                detail.merge(a.getCategorySlug(), w, Double::sum);
                if (recentDetail == null) recentDetail = a.getCategorySlug();
            }
            if (a.getAction() == ActivityType.SEARCH && a.getKeyword() != null) {
                for (String gu : BUSAN_GU) {
                    if (a.getKeyword().contains(gu)) {
                        area.merge(gu, w, Double::sum);
                        if (recentArea == null) recentArea = gu;
                    }
                }
            }
        }

        boolean cold = acts.isEmpty();
        if (type.isEmpty()) {   // 행동이 없거나 카테고리 신호가 없으면 온보딩 선호로 채운다
            for (String s : prefSlugs(userId)) type.merge(s, 1.0, Double::sum);
        }
        return new UserTasteProfile(
                normalize(type), normalize(area), normalize(detail),
                seen, recentArea, recentDetail, cold);
    }

    // ── 추천 ────────────────────────────────────────────────

    public List<PersonalRecoDto> recommend(Long userId, String lang) {
        UserTasteProfile p = buildProfile(userId);
        if (p.isEmpty()) return List.of();

        String language = RecoReasonText.language(lang);
        // 취향 프로필의 구·소분류는 국문이다. 외국어 목록은 주소·소분류가 그 언어라 그대로 맞춰 보면
        // 구가 하나도 안 맞아, 같은 사람이라도 언어에 따라 추천이 달라졌다. 국문 목록에서 같은 번호로 찾는다.
        Map<String, TourPlaceDto> korean = new HashMap<>();
        if (!"ko".equals(language)) {
            for (TourPlaceDto k : tourismService.allList("ko")) korean.put(k.getId(), k);
        }
        List<PersonalRecoDto> scored = new ArrayList<>();
        for (TourPlaceDto place : tourismService.allList(language)) {
            if ("25".equals(place.getType())) continue;   // 여행코스 제외
            PersonalRecoDto d = scoreOne(place, korean.getOrDefault(place.getId(), place), p, language);
            if (d != null) scored.add(d);
        }
        scored.sort(Comparator.comparingDouble(PersonalRecoDto::score).reversed());
        return scored.subList(0, Math.min(TOP_N, scored.size()));
    }

    /**
     * @param x  화면 언어 목록의 장소(이름·소분류 표시에 쓴다)
     * @param ko 같은 번호의 국문 장소(구·소분류 맞춰 보기에 쓴다). 국문 화면이면 x 와 같다.
     */
    private PersonalRecoDto scoreOne(TourPlaceDto x, TourPlaceDto ko, UserTasteProfile p, String lang) {
        String slug = slugOfType(x.getType());
        String gu = ActivityLogService.areaFromAddr(ko.getAddr());
        String detail = x.getTypeDetailName();
        String detailKo = ko.getTypeDetailName() != null ? ko.getTypeDetailName() : detail;

        double sType   = slug == null   ? 0 : p.typeWeight().getOrDefault(slug, 0.0);
        double sArea   = gu == null     ? 0 : p.areaWeight().getOrDefault(gu, 0.0);
        // 예전 기록은 소분류가 그 화면 언어로 남아 있어 둘 다 본다.
        double sDetail = detail == null ? 0 : Math.max(p.detailWeight().getOrDefault(detailKo, 0.0),
                                                        p.detailWeight().getOrDefault(detail, 0.0));
        double sRecency = 0;
        if (gu != null && gu.equals(p.recentArea())) sRecency += 0.5;
        if (detail != null && (detailKo.equals(p.recentDetail()) || detail.equals(p.recentDetail()))) sRecency += 0.5;

        double score = wType * sType + wArea * sArea + wDetail * sDetail + wRecency * sRecency;
        if (score <= 0) return null;
        if (p.seen().contains(x.getId())) score *= seenPenalty;

        List<String> reasons = new ArrayList<>();
        // 근거 태그는 화면 언어로 만든다(RecoReasonText). 국문이면 예전과 같은 "관광지 선호" 꼴.
        if (sType > 0)   reasons.add(reasonText.likes(slug, lang));
        if (sArea > 0)   reasons.add(reasonText.area(gu, lang));
        if (sDetail > 0) reasons.add(reasonText.oftenViewed(detail, lang));
        if (reasons.isEmpty()) return null;

        return new PersonalRecoDto(x.getId(), x.getTitle(), x.getType(), slug,
                x.getImage(), round(score), reasons, null);
    }

    // ── helpers ─────────────────────────────────────────────

    private List<String> prefSlugs(Long userId) {
        return tourPrefRepository.findById(userId)
                .map(pref -> pref.getCategories() == null || pref.getCategories().isBlank()
                        ? List.<String>of()
                        : Arrays.stream(pref.getCategories().split(",")).map(String::trim)
                                .filter(s -> !s.isEmpty()).toList())
                .orElseGet(List::of);
    }

    /** contentTypeId → 탐색 칩 슬러그. */
    static String slugOfType(String contentTypeId) {
        if (contentTypeId == null) return null;
        return switch (contentTypeId) {
            case "12" -> "attraction";
            case "14" -> "culture";
            case "15" -> "festival";
            case "28" -> "leisure";
            case "38" -> "shopping";
            default -> null;
        };
    }

    private static Map<String, Double> normalize(Map<String, Double> m) {
        double max = m.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max <= 0) return Map.of();
        Map<String, Double> out = new HashMap<>();
        m.forEach((k, v) -> out.put(k, v / max));
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
