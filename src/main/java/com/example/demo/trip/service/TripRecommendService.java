package com.example.demo.trip.service;

import com.example.demo.trip.dto.*;
import com.example.demo.trip.dto.TripRecommendResponse.*;
import com.example.demo.trip.entity.PlaceEntity;
import com.example.demo.trip.repository.PlaceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripRecommendService {

    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;

    // ── JSON 데이터 캐시 ──
    private Map<String, Double> foodScores   = new HashMap<>();
    private Map<String, Double> cafeScores   = new HashMap<>();
    private Map<String, Double> foreignCoverage = new HashMap<>();
    private Map<String, Object> modelLookup  = new HashMap<>();

    // ── 리소스 경로 ──
    private static final String FOOD_SCORES_PATH   = "model/tourism/food/food_scores.json";
    private static final String CAFE_SCORES_PATH   = "model/tourism/cafe/cafe_scores.json";
    private static final String MODEL_LOOKUP_PATH  = "model/tourism/model_lookup.json";

    @PostConstruct
    public void loadJsonData() {
        try {
            foodScores  = loadScoreJson(FOOD_SCORES_PATH);
            cafeScores  = loadScoreJson(CAFE_SCORES_PATH);
            loadModelLookup();
            log.info("[TripService] JSON 데이터 로드 완료: food={}, cafe={}", foodScores.size(), cafeScores.size());
        } catch (Exception e) {
            log.error("[TripService] JSON 로드 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> loadScoreJson(String path) throws Exception {
        ClassPathResource res = new ClassPathResource(path);
        try (InputStream is = res.getInputStream()) {
            Map<String, Object> raw = objectMapper.readValue(is, new TypeReference<>() {});
            Map<String, Double> scores = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                Object v = entry.getValue();
                if (v instanceof Number) {
                    scores.put(entry.getKey(), ((Number) v).doubleValue());
                }
            }
            return scores;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadModelLookup() throws Exception {
        ClassPathResource res = new ClassPathResource(MODEL_LOOKUP_PATH);
        try (InputStream is = res.getInputStream()) {
            modelLookup = objectMapper.readValue(is, new TypeReference<>() {});
            Object fc = modelLookup.get("foreign_coverage");
            if (fc instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) fc).entrySet()) {
                    if (e.getValue() instanceof Number) {
                        foreignCoverage.put(e.getKey().toString(), ((Number) e.getValue()).doubleValue());
                    }
                }
            }
        }
    }

    // ================================================================
    //  핵심: 추천 + 일정 생성
    // ================================================================
    public TripRecommendResponse recommend(TripRecommendRequest req) {

        List<PlaceEntity> allTourism = placeRepository.findByCategory("tourism");
        List<PlaceEntity> allFood    = placeRepository.findByCategory("food");
        List<PlaceEntity> allCafe    = placeRepository.findByCategory("cafe");

        // 점수 계산 및 정렬
        List<ScoredPlace> scoredTourism = score(allTourism, req, "tourism");
        List<ScoredPlace> scoredFood    = scoreWithJson(allFood, foodScores, req);
        List<ScoredPlace> scoredCafe    = scoreWithJson(allCafe, cafeScores, req);

        // 상위 추천 목록
        List<RecommendedPlace> tourismList = toRecommendedList(scoredTourism, 5);
        List<RecommendedPlace> foodList    = toRecommendedList(scoredFood, 5);
        List<RecommendedPlace> cafeList    = toRecommendedList(scoredCafe, 5);

        // 일정 생성
        List<DayScheduleDto> schedule = buildSchedule(req, scoredTourism, scoredFood, scoredCafe);

        TripRecommendResponse resp = new TripRecommendResponse();
        resp.setTourismList(tourismList);
        resp.setFoodList(foodList);
        resp.setCafeList(cafeList);
        resp.setSchedule(schedule);
        return resp;
    }

    // ── 점수 계산 (DB 기반 관광지) ──
    private List<ScoredPlace> score(List<PlaceEntity> places, TripRecommendRequest req, String cat) {
        return places.stream().map(p -> {
                    double s = p.getBaseScore();
                    s = applyUserBonus(s, p.getName(), p.getArea(), req);
                    s *= foreignCoverage.getOrDefault(p.getName(), 1.0);
                    return new ScoredPlace(p, s, buildReason(p.getName(), p.getArea(), req));
                })
                .sorted(Comparator.comparingDouble(ScoredPlace::score).reversed())
                .collect(Collectors.toList());
    }

    // ── 점수 계산 (JSON 점수 기반 음식/카페) ──
    private List<ScoredPlace> scoreWithJson(List<PlaceEntity> places,
                                            Map<String, Double> jsonScores,
                                            TripRecommendRequest req) {
        return places.stream().map(p -> {
                    double base = jsonScores.getOrDefault(p.getName(), p.getBaseScore());
                    double s    = applyUserBonus(base, p.getName(), p.getArea(), req);
                    s *= foreignCoverage.getOrDefault(p.getName(), 1.0);
                    return new ScoredPlace(p, s, buildReason(p.getName(), p.getArea(), req));
                })
                .sorted(Comparator.comparingDouble(ScoredPlace::score).reversed())
                .collect(Collectors.toList());
    }

    // ── 사용자 조건 보정 ──
    private double applyUserBonus(double base, String name, String area, TripRecommendRequest req) {
        double s = base;

        // 꼭 가고 싶은 장소
        if (req.getMustVisit() != null && req.getMustVisit().stream().anyMatch(name::contains)) {
            s += 0.30;
        }
        // 선호 지역
        if (req.getPreferredAreas() != null && area != null &&
                req.getPreferredAreas().stream().anyMatch(area::contains)) {
            s += 0.15;
        }
        // 카테고리 취향
        if (req.getCategories() != null) {
            if (req.getCategories().contains("local"))  s += 0.08;
            if (req.getCategories().contains("photo"))  s += 0.05;
            if (req.getCategories().contains("cafe"))   s += 0.05;
        }
        // 예산 보정
        if ("high".equals(req.getBudget()))   s += 0.03;
        if ("low".equals(req.getBudget()))    s -= 0.02;

        return s;
    }

    // ── 추천 이유 문자열 ──
    private String buildReason(String name, String area, TripRecommendRequest req) {
        List<String> reasons = new ArrayList<>();
        if (req.getMustVisit() != null && req.getMustVisit().stream().anyMatch(name::contains))
            reasons.add("꼭 가고 싶은 장소 반영");
        if (req.getPreferredAreas() != null && area != null &&
                req.getPreferredAreas().stream().anyMatch(area::contains))
            reasons.add("선호 지역 우선 반영");
        if (req.getCategories() != null && req.getCategories().contains("local"))
            reasons.add("로컬 체험 요소 반영");
        if (req.getCategories() != null && req.getCategories().contains("photo"))
            reasons.add("사진/감성 포인트");
        if (reasons.isEmpty()) reasons.add("기본 점수 우수");
        return String.join(", ", reasons);
    }

    // ── 일정 생성 ──
    private List<DayScheduleDto> buildSchedule(TripRecommendRequest req,
                                               List<ScoredPlace> tourism,
                                               List<ScoredPlace> food,
                                               List<ScoredPlace> cafe) {
        List<DayScheduleDto> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();

        // 앵커 좌표 (명동 기본값)
        double anchorLat = 37.5636;
        double anchorLng = 126.9827;

        int tourIdx = 0, foodIdx = 0, cafeIdx = 0;

        for (int day = 1; day <= req.getTravelDays(); day++) {
            DayScheduleDto dayDto = new DayScheduleDto();
            dayDto.setDay(day);
            List<SlotDto> slots = new ArrayList<>();
            int currentHour = req.getStartHour();

            // ── 관광 슬롯 ──
            for (int t = 0; t < req.getDailyTourism() && currentHour < req.getEndHour(); t++) {
                ScoredPlace pick = pickNext(tourism, usedNames, anchorLat, anchorLng, 5.0, tourIdx);
                if (pick == null) break;
                usedNames.add(pick.entity.getName());
                tourIdx++;

                SlotDto slot = toSlot(String.format("%02d:00", currentHour), "tourism", pick, req, tourism);
                slots.add(slot);

                // 앵커 업데이트
                if (pick.entity.getLat() != null) {
                    anchorLat = pick.entity.getLat();
                    anchorLng = pick.entity.getLng();
                }
                currentHour += (req.getDailyTourism() > 1) ? 2 : 3;
            }

            // ── 식사 슬롯 ──
            for (int m = 0; m < req.getDailyMeal() && currentHour < req.getEndHour(); m++) {
                ScoredPlace pick = pickNext(food, usedNames, anchorLat, anchorLng, 3.0, foodIdx);
                if (pick == null) break;
                usedNames.add(pick.entity.getName());
                foodIdx++;

                SlotDto slot = toSlot(String.format("%02d:00", currentHour), "food", pick, req, food);
                slots.add(slot);
                currentHour += 1;
            }

            // ── 카페 슬롯 ──
            for (int c = 0; c < req.getDailyCafe() && currentHour < req.getEndHour(); c++) {
                ScoredPlace pick = pickNext(cafe, usedNames, anchorLat, anchorLng, 2.0, cafeIdx);
                if (pick == null) break;
                usedNames.add(pick.entity.getName());
                cafeIdx++;

                SlotDto slot = toSlot(String.format("%02d:00", currentHour), "cafe", pick, req, cafe);
                slots.add(slot);
                currentHour += 1;
            }

            dayDto.setSlots(slots);
            result.add(dayDto);
        }
        return result;
    }

    // ── 거리 기반으로 다음 장소 선택 ──
    private ScoredPlace pickNext(List<ScoredPlace> candidates, Set<String> used,
                                 double anchorLat, double anchorLng,
                                 double maxKm, int startIdx) {
        for (ScoredPlace sp : candidates) {
            if (used.contains(sp.entity.getName())) continue;
            if (sp.entity.getLat() == null) return sp; // 좌표 없으면 거리 체크 안 함
            double dist = haversine(anchorLat, anchorLng, sp.entity.getLat(), sp.entity.getLng());
            if (dist <= maxKm) return sp;
        }
        // maxKm 내에 없으면 가장 높은 점수의 미사용 장소
        return candidates.stream()
                .filter(sp -> !used.contains(sp.entity.getName()))
                .findFirst()
                .orElse(null);
    }

    // ── SlotDto 변환 ──
    private SlotDto toSlot(String time, String category, ScoredPlace pick,
                           TripRecommendRequest req, List<ScoredPlace> pool) {
        SlotDto slot = new SlotDto();
        slot.setTime(time);
        slot.setCategory(category);
        slot.setPlaceName(pick.entity.getName());
        slot.setReason(pick.reason);
        slot.setScore(pick.score);
        slot.setLat(pick.entity.getLat());
        slot.setLng(pick.entity.getLng());
        slot.setAddress(pick.entity.getAddress());
        slot.setImageUrl(pick.entity.getImageUrl());

        double dist = 0.0;
        // alternatives
        List<AlternativeDto> alts = pool.stream()
                .filter(sp -> !sp.entity.getName().equals(pick.entity.getName()))
                .limit(5)
                .map(sp -> {
                    AlternativeDto alt = new AlternativeDto();
                    alt.setPlaceName(sp.entity.getName());
                    alt.setScore(sp.score);
                    alt.setLat(sp.entity.getLat());
                    alt.setLng(sp.entity.getLng());
                    if (pick.entity.getLat() != null && sp.entity.getLat() != null) {
                        alt.setDistanceKm(Math.round(haversine(
                                pick.entity.getLat(), pick.entity.getLng(),
                                sp.entity.getLat(), sp.entity.getLng()) * 100.0) / 100.0);
                    }
                    return alt;
                })
                .collect(Collectors.toList());
        slot.setAlternatives(alts);
        slot.setDistanceKm(dist);
        return slot;
    }

    // ── 장소 상세 ──
    public PlaceDetailDto getPlaceDetail(Long placeId) {
        return placeRepository.findById(placeId).map(p -> {
            PlaceDetailDto dto = new PlaceDetailDto();
            dto.setId(p.getId());
            dto.setName(p.getName());
            dto.setCategory(p.getCategory());
            dto.setAddress(p.getAddress());
            dto.setLat(p.getLat());
            dto.setLng(p.getLng());
            dto.setAvgRating(p.getAvgRating());
            dto.setReviewCount(p.getReviewCount());
            dto.setDescription(p.getDescription());
            dto.setImageUrl(p.getImageUrl());
            dto.setOpenHours(p.getOpenHours());
            dto.setPhone(p.getPhone());
            return dto;
        }).orElse(null);
    }

    // ── Haversine 거리 계산 (km) ──
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<RecommendedPlace> toRecommendedList(List<ScoredPlace> scored, int limit) {
        return scored.stream().limit(limit).map(sp -> {
            RecommendedPlace rp = new RecommendedPlace();
            rp.setName(sp.entity.getName());
            rp.setScore(Math.round(sp.score * 10000.0) / 10000.0);
            rp.setReason(sp.reason);
            rp.setLat(sp.entity.getLat());
            rp.setLng(sp.entity.getLng());
            rp.setAddress(sp.entity.getAddress());
            rp.setImageUrl(sp.entity.getImageUrl());
            rp.setAvgRating(sp.entity.getAvgRating());
            rp.setReviewCount(sp.entity.getReviewCount());
            return rp;
        }).collect(Collectors.toList());
    }

    // ── 내부 점수 래퍼 ──
    private record ScoredPlace(PlaceEntity entity, double score, String reason) {}
}