package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.*;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.ReservationPlacePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReservationPlaceAdminService {
    private final ReservationPlacePolicyRepository repository;
    private final TourApiReservationService tourApi;

    @Transactional
    public ReservationPlacePolicy update(String contentId, PlacePolicyUpdateRequest request, Long adminId) {
        if (!contentId.matches("[0-9]{1,30}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (request.feeStatus() != PlaceFeeStatus.PAID && request.reservationEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유료로 확인된 장소만 예약을 허용할 수 있습니다.");
        }
        ReservationPlacePolicy policy = repository.findById(contentId).orElseGet(ReservationPlacePolicy::new);
        if (!Objects.equals(policy.getVersion(), request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "설정이 변경되었습니다. 새로고침 후 다시 확인해 주세요.");
        }
        TourApiPlaceDto place = tourApi.findDetail(contentId, null, "ko");
        if (place == null || !contentId.equals(place.getContentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장소 정보를 확인할 수 없어 저장하지 않았습니다.");
        }
        policy.setContentId(contentId);
        policy.setContentTypeId(place.getContentTypeId());
        policy.setPlaceTitle(place.getTitle());
        policy.setFeeStatus(request.feeStatus());
        policy.setReservationEnabled(request.reservationEnabled());
        policy.setFeeNote(request.feeNote().trim());
        policy.setReviewedBy(adminId);
        policy.setReviewedAt(LocalDateTime.now());
        return repository.saveAndFlush(policy);
    }
}
