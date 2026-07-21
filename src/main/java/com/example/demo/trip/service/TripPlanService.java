package com.example.demo.trip.service;

import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.dto.TripRecommendResponse.DayScheduleDto;
import com.example.demo.trip.dto.TripRecommendResponse.SlotDto;
import com.example.demo.trip.entity.TripPlan;
import com.example.demo.trip.entity.TripPlanDay;
import com.example.demo.trip.entity.TripPlanSlot;
import com.example.demo.trip.repository.TripPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripPlanService {

    private final TripPlanRepository tripPlanRepository;

    // ────────────────────────────────────────────
    //  저장
    // ────────────────────────────────────────────
    @Transactional
    public Long save(String userId, TripPlanDto.SaveRequest req) {
        var tripReq  = req.getRequest();
        var tripResp = req.getResponse();

        // 제목 자동 생성
        String title = req.getTitle();
        if (title == null || title.isBlank()) {
            String areas = (tripReq.getPreferredAreas() != null && !tripReq.getPreferredAreas().isEmpty())
                    ? String.join("·", tripReq.getPreferredAreas().stream().limit(2).toList())
                    : "서울";
            int nights = Math.max(0, tripReq.getTravelDays() - 1);
            title = areas + " " + nights + "박" + tripReq.getTravelDays() + "일";
        }

        TripPlan plan = TripPlan.builder()
                .userId(userId)
                .title(title)
                .age(tripReq.getAge())
                .companion(tripReq.getCompanion())
                .budget(tripReq.getBudget())
                .travelDays(tripReq.getTravelDays())
                .startDate(tripReq.getStartDate())
                .startHour(tripReq.getStartHour())
                .endHour(tripReq.getEndHour())
                .intensity(tripReq.getIntensity())
                .preferredAreas(listToStr(tripReq.getPreferredAreas()))
                .mustVisit(listToStr(tripReq.getMustVisit()))
                .categories(listToStr(tripReq.getCategories()))
                .styleDescription(tripReq.getStyleDescription())
                .build();

        // 일별 슬롯 저장
        if (tripResp != null && tripResp.getSchedule() != null) {
            for (DayScheduleDto dayDto : tripResp.getSchedule()) {
                TripPlanDay day = TripPlanDay.builder()
                        .tripPlan(plan)
                        .day(dayDto.getDay())
                        .build();

                if (dayDto.getSlots() != null) {
                    int order = 0;
                    for (SlotDto slotDto : dayDto.getSlots()) {
                        day.getSlots().add(TripPlanSlot.builder()
                                .tripPlanDay(day)
                                .slotOrder(order++)
                                .time(slotDto.getTime())
                                .category(slotDto.getCategory())
                                .placeName(slotDto.getPlaceName())
                                .reason(slotDto.getReason())
                                .score(slotDto.getScore())
                                .lat(slotDto.getLat())
                                .lng(slotDto.getLng())
                                .address(slotDto.getAddress())
                                .imageUrl(slotDto.getImageUrl())
                                .avgRating(slotDto.getAvgRating())
                                .reviewCount(slotDto.getReviewCount())
                                .durationMinutes(slotDto.getDurationMinutes())
                                .build());
                    }
                }
                plan.getDays().add(day);
            }
        }

        TripPlan saved = tripPlanRepository.save(plan);
        log.info("[TripPlanService] 저장 — userId={}, planId={}, title={}", userId, saved.getId(), title);
        return saved.getId();
    }

    // ────────────────────────────────────────────
    //  목록 조회
    // ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<TripPlanDto.Summary> listByUser(String userId) {
        return tripPlanRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toSummary).collect(Collectors.toList());
    }

    // ────────────────────────────────────────────
    //  상세 조회
    // ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public TripPlanDto.Detail getDetail(String userId, Long planId) {
        return tripPlanRepository.findWithDaysAndSlots(planId, userId)
                .map(this::toDetail).orElse(null);
    }

    // ────────────────────────────────────────────
    //  삭제
    // ────────────────────────────────────────────
    @Transactional
    public boolean delete(String userId, Long planId) {
        return tripPlanRepository.findByIdAndUserId(planId, userId)
                .map(plan -> { tripPlanRepository.delete(plan); return true; })
                .orElse(false);
    }

    // ────────────────────────────────────────────
    //  변환 헬퍼
    // ────────────────────────────────────────────
    private TripPlanDto.Summary toSummary(TripPlan p) {
        TripPlanDto.Summary s = new TripPlanDto.Summary();
        s.setId(p.getId()); s.setTitle(p.getTitle());
        s.setTravelDays(p.getTravelDays());
        s.setStartDate(p.getStartDate());
        s.setPreferredAreas(strToList(p.getPreferredAreas()));
        s.setCompanion(p.getCompanion());
        s.setCreatedAt(p.getCreatedAt());
        return s;
    }

    private TripPlanDto.Detail toDetail(TripPlan p) {
        TripPlanDto.Detail d = new TripPlanDto.Detail();
        d.setId(p.getId()); d.setTitle(p.getTitle());
        d.setAge(p.getAge()); d.setCompanion(p.getCompanion());
        d.setBudget(p.getBudget()); d.setTravelDays(p.getTravelDays());
        d.setStartDate(p.getStartDate());
        d.setStartHour(p.getStartHour()); d.setEndHour(p.getEndHour());
        d.setIntensity(p.getIntensity());
        d.setPreferredAreas(strToList(p.getPreferredAreas()));
        d.setMustVisit(strToList(p.getMustVisit()));
        d.setCategories(strToList(p.getCategories()));
        d.setStyleDescription(p.getStyleDescription());
        d.setCreatedAt(p.getCreatedAt());

        List<TripPlanDto.DayDto> dayDtos = new ArrayList<>();
        for (TripPlanDay day : p.getDays()) {
            TripPlanDto.DayDto dd = new TripPlanDto.DayDto();
            dd.setDay(day.getDay());
            dd.setSlots(day.getSlots().stream().map(sl -> {
                TripPlanDto.SlotDto sd = new TripPlanDto.SlotDto();
                sd.setSlotOrder(sl.getSlotOrder());
                sd.setTime(sl.getTime()); sd.setCategory(sl.getCategory());
                sd.setPlaceName(sl.getPlaceName()); sd.setReason(sl.getReason());
                sd.setScore(sl.getScore()); sd.setLat(sl.getLat()); sd.setLng(sl.getLng());
                sd.setAddress(sl.getAddress());
                sd.setImageUrl(sl.getImageUrl());
                sd.setAvgRating(sl.getAvgRating());
                sd.setReviewCount(sl.getReviewCount());
                sd.setDurationMinutes(sl.getDurationMinutes());
                return sd;
            }).collect(Collectors.toList()));
            dayDtos.add(dd);
        }
        d.setDays(dayDtos);
        return d;
    }

    private String listToStr(List<String> list) {
        return (list == null || list.isEmpty()) ? "" : String.join(",", list);
    }

    private List<String> strToList(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
