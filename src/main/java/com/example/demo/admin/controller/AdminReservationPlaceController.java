package com.example.demo.admin.controller;

import com.example.demo.reservation.dto.*;
import com.example.demo.reservation.entity.ReservationPlacePolicy;
import com.example.demo.reservation.repository.ReservationPlacePolicyRepository;
import com.example.demo.reservation.service.*;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/reservation-places")
@RequiredArgsConstructor
public class AdminReservationPlaceController {
    private final TourApiReservationService tourApi;
    private final ReservationPlacePolicyRepository repository;
    private final ReservationPlaceAdminService adminService;

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String keyword,
                                    @RequestParam(defaultValue = "") String contentTypeId,
                                    @RequestParam(defaultValue = "0") int page) {
        // Filter before pagination so searching also finds places outside the first API page.
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        List<TourApiPlaceDto> all = tourApi.findPlaces(null, contentTypeId, "ko", 1, 10000).stream()
                .filter(p -> query.isBlank() || (p.getContentId() + " " + p.getTitle() + " " + p.getAddress())
                        .toLowerCase(Locale.ROOT).contains(query)).toList();
        int safePage = Math.max(0, Math.min(page, Math.max(0, (all.size() - 1) / 20)));
        List<TourApiPlaceDto> places = all.stream().skip((long) safePage * 20).limit(20).toList();
        Map<String, ReservationPlacePolicy> policies = repository.findAllById(places.stream()
                .map(TourApiPlaceDto::getContentId).toList()).stream()
                .collect(Collectors.toMap(ReservationPlacePolicy::getContentId, Function.identity()));
        return Map.of("places", places, "policies", policies, "page", safePage, "total", all.size());
    }

    @PutMapping("/{contentId}")
    public ReservationPlacePolicy update(@PathVariable String contentId,
                                         @Valid @RequestBody PlacePolicyUpdateRequest request) {
        return adminService.update(contentId, request, SecurityUtil.getCurrentUserId());
    }

    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(Map.of("message", "다른 관리자가 설정을 변경했습니다. 새로고침 후 다시 확인해 주세요."));
    }
}
