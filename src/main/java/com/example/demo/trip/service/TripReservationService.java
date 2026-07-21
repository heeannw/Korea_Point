package com.example.demo.trip.service;

import com.example.demo.trip.dto.TripReservationDto;
import com.example.demo.trip.entity.ReservationStatus;
import com.example.demo.trip.entity.TripPlan;
import com.example.demo.trip.entity.TripReservation;
import com.example.demo.trip.repository.TripPlanRepository;
import com.example.demo.trip.repository.TripReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TripReservationService {

    private final TripReservationRepository reservationRepository;
    private final TripPlanRepository tripPlanRepository;

    @Transactional
    public Long create(String userId, TripReservationDto.CreateRequest request) {
        TripPlan plan = tripPlanRepository.findWithDaysAndSlots(request.getTripPlanId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("저장된 일정을 찾을 수 없습니다."));
        boolean placeExists = plan.getDays().stream()
                .flatMap(day -> day.getSlots().stream())
                .anyMatch(slot -> slot.getPlaceName().equals(request.getPlaceName()));
        if (!placeExists) {
            throw new IllegalArgumentException("일정에 포함된 장소만 예약 요청할 수 있습니다.");
        }

        TripReservation reservation = TripReservation.builder()
                .userId(userId)
                .tripPlan(plan)
                .placeName(request.getPlaceName())
                .visitDate(request.getVisitDate())
                .visitTime(request.getVisitTime())
                .partySize(request.getPartySize())
                .requestNote(request.getRequestNote())
                .build();
        return reservationRepository.save(reservation).getId();
    }

    @Transactional(readOnly = true)
    public List<TripReservationDto.Response> list(String userId) {
        return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public boolean cancel(String userId, Long reservationId) {
        return reservationRepository.findByIdAndUserId(reservationId, userId)
                .map(reservation -> {
                    reservation.setStatus(ReservationStatus.CANCELLED);
                    return true;
                }).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<TripReservationDto.Response> listAll() {
        return reservationRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public boolean updateStatus(Long reservationId, ReservationStatus status) {
        if (status != ReservationStatus.CONFIRMED && status != ReservationStatus.REJECTED) {
            throw new IllegalArgumentException("예약 상태는 CONFIRMED 또는 REJECTED로만 변경할 수 있습니다.");
        }
        return reservationRepository.findById(reservationId).map(reservation -> {
            reservation.setStatus(status);
            return true;
        }).orElse(false);
    }

    private TripReservationDto.Response toResponse(TripReservation reservation) {
        TripReservationDto.Response response = new TripReservationDto.Response();
        response.setId(reservation.getId());
        response.setTripPlanId(reservation.getTripPlan().getId());
        response.setTripTitle(reservation.getTripPlan().getTitle());
        response.setPlaceName(reservation.getPlaceName());
        response.setVisitDate(reservation.getVisitDate());
        response.setVisitTime(reservation.getVisitTime());
        response.setPartySize(reservation.getPartySize());
        response.setRequestNote(reservation.getRequestNote());
        response.setStatus(reservation.getStatus());
        response.setCreatedAt(reservation.getCreatedAt());
        return response;
    }
}
