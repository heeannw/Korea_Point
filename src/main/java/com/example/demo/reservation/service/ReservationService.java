package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.repository.TourReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final TourReservationRepository tourReservationRepository;

    @Transactional
    public TourReservation create(Long userId, ReservationFormDto form) {
        TourReservation reservation = TourReservation.builder()
                .userId(userId)
                .reservationNo(createReservationNo(userId, form))
                .contentId(form.getContentId())
                .contentTypeId(form.getContentTypeId())
                .placeTitle(form.getPlaceTitle())
                .reservationDate(form.getReservationDate())
                .reservationTime(form.getReservationTime())
                .peopleCount(form.getPeopleCount())
                .memo(form.getMemo())
                .build();

        return tourReservationRepository.save(reservation);
    }

    public List<TourReservation> findMyReservations(Long userId) {
        return tourReservationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public TourReservation findMine(Long id, Long userId) {
        return tourReservationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다."));
    }

    @Transactional
    public TourReservation update(Long id, Long userId, ReservationFormDto form) {
        TourReservation reservation = findMine(id, userId);
        if ("예약 취소".equals(reservation.getStatus())) {
            throw new IllegalStateException("취소된 예약은 수정할 수 없습니다.");
        }
        reservation.setReservationDate(form.getReservationDate());
        reservation.setReservationTime(form.getReservationTime());
        reservation.setPeopleCount(form.getPeopleCount());
        reservation.setMemo(form.getMemo());
        return reservation;
    }

    @Transactional
    public TourReservation cancel(Long id, Long userId) {
        TourReservation reservation = findMine(id, userId);
        reservation.setStatus("예약 취소");
        return reservation;
    }

    private String createReservationNo(Long userId, ReservationFormDto form) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "KP-" + date + "-" + suffix;
    }
}
