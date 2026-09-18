package com.example.demo.trip.service;

import com.example.demo.config.MessageArgumentException;
import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.dto.TripRecommendRequest;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.entity.ReservationStatus;
import com.example.demo.trip.dto.TripRecommendResponse.DayScheduleDto;
import com.example.demo.trip.dto.TripRecommendResponse.SlotDto;
import com.example.demo.trip.entity.TripPlan;
import com.example.demo.trip.entity.TripPlanDay;
import com.example.demo.trip.entity.TripPlanSlot;
import com.example.demo.trip.repository.TripPlanRepository;
import com.example.demo.tourism.dto.SavedPlaceDto;
import com.example.demo.tourism.service.SavedPlaceService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripPlanService {

    private final TripPlanRepository tripPlanRepository;
    private final TripRecommendService tripRecommendService;
    // 저장(찜) 기능 자체는 관광 모듈 소관이라 새로 만들지 않고 기존 서비스를 그대로 읽기만 한다.
    private final SavedPlaceService savedPlaceService;

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
                    : "부산";
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
                .arrivalAirport(tripReq.getArrivalAirport())
                .arrivalTime(tripReq.getArrivalTime())
                .arrivalReadyMinutes(tripReq.getArrivalReadyMinutes())
                .departureAirport(tripReq.getDepartureAirport())
                .departureTime(tripReq.getDepartureTime())
                .departureLeadMinutes(tripReq.getDepartureLeadMinutes())
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

    @Transactional
    public boolean updateTitle(String userId, Long planId, String title) {
        String normalizedTitle = title == null ? "" : title.trim();
        if (normalizedTitle.isBlank()) {
            throw new MessageArgumentException("trip.api.titleRequired", "일정 제목을 입력해 주세요.");
        }
        if (normalizedTitle.length() > 200) {
            throw new MessageArgumentException("trip.api.titleTooLong", "일정 제목은 200자 이하로 입력해 주세요.");
        }
        return tripPlanRepository.findByIdAndUserId(planId, userId)
                .map(plan -> {
                    plan.setTitle(normalizedTitle);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public Long copy(String userId, Long planId) {
        TripPlan source = tripPlanRepository.findWithDaysAndSlots(planId, userId)
                .orElseThrow(() -> new MessageArgumentException("trip.api.notFound", "계획을 찾을 수 없습니다."));
        TripPlan copied = TripPlan.builder()
                .userId(userId)
                .title(source.getTitle() + " 복사본")
                .age(source.getAge())
                .companion(source.getCompanion())
                .budget(source.getBudget())
                .travelDays(source.getTravelDays())
                .startDate(source.getStartDate())
                .startHour(source.getStartHour())
                .endHour(source.getEndHour())
                .arrivalAirport(source.getArrivalAirport())
                .arrivalTime(source.getArrivalTime())
                .arrivalReadyMinutes(source.getArrivalReadyMinutes())
                .departureAirport(source.getDepartureAirport())
                .departureTime(source.getDepartureTime())
                .departureLeadMinutes(source.getDepartureLeadMinutes())
                .intensity(source.getIntensity())
                .preferredAreas(source.getPreferredAreas())
                .mustVisit(source.getMustVisit())
                .categories(source.getCategories())
                .styleDescription(source.getStyleDescription())
                .build();

        for (TripPlanDay sourceDay : source.getDays()) {
            TripPlanDay copiedDay = TripPlanDay.builder()
                    .tripPlan(copied)
                    .day(sourceDay.getDay())
                    .build();
            for (TripPlanSlot sourceSlot : sourceDay.getSlots()) {
                copiedDay.getSlots().add(TripPlanSlot.builder()
                        .tripPlanDay(copiedDay)
                        .slotOrder(sourceSlot.getSlotOrder())
                        .time(sourceSlot.getTime())
                        .category(sourceSlot.getCategory())
                        .placeName(sourceSlot.getPlaceName())
                        .reason(sourceSlot.getReason())
                        .score(sourceSlot.getScore())
                        .lat(sourceSlot.getLat())
                        .lng(sourceSlot.getLng())
                        .address(sourceSlot.getAddress())
                        .imageUrl(sourceSlot.getImageUrl())
                        .avgRating(sourceSlot.getAvgRating())
                        .reviewCount(sourceSlot.getReviewCount())
                        .durationMinutes(sourceSlot.getDurationMinutes())
                        .build());
            }
            copied.getDays().add(copiedDay);
        }
        return tripPlanRepository.save(copied).getId();
    }

    // ────────────────────────────────────────────
    //  "상세 페이지에서 보기" — 저장된 일정 + 실시간 추천 리스트/대체 후보
    // ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public TripRecommendResponse buildResultView(String userId, Long planId) {
        TripPlan plan = tripPlanRepository.findWithDaysAndSlots(planId, userId).orElse(null);
        if (plan == null) return null;

        TripRecommendRequest req = new TripRecommendRequest();
        req.setAge(plan.getAge());
        req.setCompanion(plan.getCompanion());
        req.setBudget(plan.getBudget());
        req.setMustVisit(strToList(plan.getMustVisit()));
        req.setCategories(strToList(plan.getCategories()));
        req.setPreferredAreas(strToList(plan.getPreferredAreas()));
        req.setStyleDescription(plan.getStyleDescription());
        req.setLang("ko");
        // startDate/travelDays가 안 채워져 있으면 축제 조회(fetchTripFestivals)가 실제 여행 기간이
        // 아니라 "오늘 하루"만 조회하게 된다 — 저장된 일정을 다시 볼 때 축제 안내가 비거나
        // 엉뚱하게 나오는 원인이라 반드시 같이 채워줘야 한다.
        req.setStartDate(plan.getStartDate());
        req.setTravelDays(plan.getTravelDays());
        // 일정 생성 시 저장한 장소를 우선 배치하던 것과 동일하게, 저장한 일정을 다시 볼 때 옆에
        // 뜨는 추천 리스트(관광/음식/카페)도 저장한 장소를 반영해서 다시 계산되게 한다.
        try {
            req.setSavedPlaceNames(savedPlaceService.list(SecurityUtil.getCurrentUserId(), null)
                    .stream().map(SavedPlaceDto::title).filter(t -> t != null && !t.isBlank()).toList());
        } catch (Exception e) {
            log.warn("저장 장소 목록 조회 실패 — 저장 장소 반영 없이 계속 진행: {}", e.getMessage());
        }

        List<DayScheduleDto> schedule = new ArrayList<>();
        for (TripPlanDay day : plan.getDays()) {
            DayScheduleDto dd = new DayScheduleDto();
            dd.setDay(day.getDay());
            dd.setSlots(day.getSlots().stream().map(sl -> {
                SlotDto sd = new SlotDto();
                sd.setTime(sl.getTime()); sd.setCategory(sl.getCategory());
                sd.setPlaceName(sl.getPlaceName()); sd.setReason(sl.getReason());
                sd.setScore(sl.getScore()); sd.setLat(sl.getLat()); sd.setLng(sl.getLng());
                sd.setAddress(sl.getAddress()); sd.setImageUrl(sl.getImageUrl());
                sd.setAvgRating(sl.getAvgRating()); sd.setReviewCount(sl.getReviewCount());
                sd.setDurationMinutes(sl.getDurationMinutes());
                return sd;
            }).collect(Collectors.toList()));
            schedule.add(dd);
        }

        return tripRecommendService.hydrateForResultView(req, schedule);
    }

    // ────────────────────────────────────────────
    //  변환 헬퍼
    // ────────────────────────────────────────────
    private TripPlanDto.Summary toSummary(TripPlan p) {
        TripPlanDto.Summary s = new TripPlanDto.Summary();
        s.setId(p.getId()); s.setTitle(p.getTitle());
        s.setTravelDays(p.getTravelDays());
        s.setStartDate(p.getStartDate());
        s.setEndDate(calculateEndDate(p));
        s.setPreferredAreas(strToList(p.getPreferredAreas()));
        s.setCompanion(p.getCompanion());
        s.setCreatedAt(p.getCreatedAt());
        String status = calculateTripStatus(p);
        s.setTripStatus(status);
        s.setTripStatusLabel(toTripStatusLabel(status));
        s.setReservationCount(p.getReservations().size());
        s.setRequestedReservationCount(countReservations(p, ReservationStatus.REQUESTED));
        s.setConfirmedReservationCount(countReservations(p, ReservationStatus.CONFIRMED));
        s.setRejectedReservationCount(countReservations(p, ReservationStatus.REJECTED));
        s.setCancelledReservationCount(countReservations(p, ReservationStatus.CANCELLED));
        return s;
    }

    private TripPlanDto.Detail toDetail(TripPlan p) {
        TripPlanDto.Detail d = new TripPlanDto.Detail();
        d.setId(p.getId()); d.setTitle(p.getTitle());
        d.setAge(p.getAge()); d.setCompanion(p.getCompanion());
        d.setBudget(p.getBudget()); d.setTravelDays(p.getTravelDays());
        d.setStartDate(p.getStartDate());
        d.setStartHour(p.getStartHour()); d.setEndHour(p.getEndHour());
        d.setArrivalAirport(p.getArrivalAirport()); d.setArrivalTime(p.getArrivalTime());
        d.setArrivalReadyMinutes(p.getArrivalReadyMinutes());
        d.setDepartureAirport(p.getDepartureAirport()); d.setDepartureTime(p.getDepartureTime());
        d.setDepartureLeadMinutes(p.getDepartureLeadMinutes());
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
                sd.setImageUrl(cleanImageUrl(sl.getImageUrl()));
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

    /**
     * 예전에 저장된 일정은 언스플래시 스톡 이미지를 그대로 갖고 있을 수 있다(실제 장소랑 안 어울리는 사진 문제로
     * 지금은 안 씀). 그런 낡은 URL은 화면에서 아예 숨기는 게 낫다 — 프론트가 이미지 없으면 알아서 처리한다.
     */
    private String cleanImageUrl(String url) {
        return (url == null || url.isBlank() || url.contains("unsplash.com")) ? null : url;
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

    private LocalDate calculateEndDate(TripPlan plan) {
        if (plan.getStartDate() == null || plan.getTravelDays() <= 0) {
            return null;
        }
        return plan.getStartDate().plusDays(plan.getTravelDays() - 1L);
    }

    private String calculateTripStatus(TripPlan plan) {
        LocalDate startDate = plan.getStartDate();
        LocalDate endDate = calculateEndDate(plan);
        if (startDate == null || endDate == null) {
            return "UNSCHEDULED";
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(startDate)) {
            return "UPCOMING";
        }
        if (today.isAfter(endDate)) {
            return "COMPLETED";
        }
        return "ONGOING";
    }

    private String toTripStatusLabel(String status) {
        return switch (status) {
            case "UPCOMING" -> "예정";
            case "ONGOING" -> "여행 중";
            case "COMPLETED" -> "완료";
            default -> "날짜 미정";
        };
    }

    private int countReservations(TripPlan plan, ReservationStatus status) {
        return (int) plan.getReservations().stream()
                .filter(reservation -> reservation.getStatus() == status)
                .count();
    }
}
