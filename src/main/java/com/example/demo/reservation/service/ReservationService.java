package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.entity.ReservationPayment;
import com.example.demo.reservation.repository.ReservationPaymentRepository;
import com.example.demo.reservation.repository.TourReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final TourReservationRepository tourReservationRepository;
    private final ReservationPaymentRepository reservationPaymentRepository;
    private final ReservationPlacePolicyService placePolicyService;
    private final PortOneGateway portOneGateway;

    @Transactional
    public TourReservation create(Long userId, ReservationFormDto form) {
        placePolicyService.requireApplicationAllowed(form.getContentId());
        requireFutureVisit(form.getReservationDate(), form.getReservationTime());
        TourReservation reservation = TourReservation.builder()
                .userId(userId)
                .reservationNo(createReservationNo(userId, form))
                .contentId(form.getContentId())
                .contentTypeId(form.getContentTypeId())
                .placeTitle(form.getPlaceTitle())
                .language(form.getLang())
                .reservationDate(form.getReservationDate())
                .reservationTime(form.getReservationTime())
                .peopleCount(form.getPeopleCount())
                .memo(form.getMemo())
                .reviewStatus(com.example.demo.reservation.entity.ReservationReviewStatus.PENDING)
                .status("승인 대기")
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
        TourReservation reservation = findMineForUpdate(id, userId);
        if (!reservation.isApplicationEditable()) throw new PlaceBookingUnavailableException();
        if (reservation.getPayment() != null && reservation.getPayment().getMerchantUid() != null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "결제를 시작한 신청은 수정할 수 없습니다. 취소 후 다시 신청해 주세요.");
        }
        placePolicyService.requireApplicationAllowed(reservation.getContentId());
        requireFutureVisit(form.getReservationDate(), form.getReservationTime());
        reservation.setReservationDate(form.getReservationDate());
        reservation.setReservationTime(form.getReservationTime());
        reservation.setPeopleCount(form.getPeopleCount());
        reservation.setMemo(form.getMemo());
        reservation.setReviewStatus(com.example.demo.reservation.entity.ReservationReviewStatus.PENDING);
        reservation.setStatus("승인 대기");
        reservation.setReviewedBy(null);
        reservation.setReviewedAt(null);
        reservation.setReviewNote(null);
        reservation.setPaymentDeadline(null);
        if (reservation.getPayment() != null) reservation.getPayment().setStatus(PaymentStatus.CANCELED);
        return reservation;
    }

    @Transactional
    public TourReservation cancel(Long id, Long userId) {
        TourReservation reservation = findMineForUpdate(id, userId);
        if ("예약 취소".equals(reservation.getStatus())) return reservation;
        ReservationPayment payment = reservation.getPayment();
        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            if (isBeforeFreeCancellationDeadline(reservation)) {
                if (payment.getImpUid() != null) {
                    portOneGateway.cancel(payment.getImpUid(), payment.getMerchantUid(), payment.getAmount());
                }
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundedAt(LocalDateTime.now());
            } else {
                payment.setStatus(PaymentStatus.FORFEITED);
            }
        } else if (payment != null) {
            payment.setStatus(PaymentStatus.CANCELED);
        }
        reservation.setStatus("예약 취소");
        return reservation;
    }

    @Transactional
    public TourReservation findMineForUpdate(Long id, Long userId) {
        return tourReservationRepository.findLockedById(id)
                .filter(reservation -> java.util.Objects.equals(userId, reservation.getUserId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    private void requireFutureVisit(LocalDate date, String time) {
        try {
            if (date != null && time != null && LocalDateTime.of(date, LocalTime.parse(time)).isAfter(LocalDateTime.now())) {
                return;
            }
        } catch (java.time.format.DateTimeParseException ignored) {
            // Invalid input is a visit-time error, not a place policy restriction.
        }
        throw new InvalidReservationTimeException();
    }

    private boolean isBeforeFreeCancellationDeadline(TourReservation reservation) {
        LocalDateTime visitAt = LocalDateTime.of(
                reservation.getReservationDate(),
                LocalTime.parse(reservation.getReservationTime()));
        return LocalDateTime.now().isBefore(visitAt.minusHours(24));
    }

    private String createReservationNo(Long userId, ReservationFormDto form) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "KP-" + date + "-" + suffix;
    }
}
