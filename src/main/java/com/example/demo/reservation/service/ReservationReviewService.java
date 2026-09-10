package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationReviewRequest;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;

@Service
@RequiredArgsConstructor
public class ReservationReviewService {
    private final TourReservationRepository reservations;
    private final ReservationPaymentRepository payments;
    private final ReservationPlacePolicyRepository policies;
    private final ReservationPlacePolicyService policyService;

    @Transactional
    public TourReservation review(Long id, Long adminId, ReservationReviewRequest request) {
        TourReservation reservation = reservations.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"PENDING".equals(reservation.getApplicationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리되었거나 취소된 신청입니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        switch (request.decision()) {
            case APPROVE -> {
                if (request.amount() == null || request.amount() < 1 || request.amount() > 10000000) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "총 예약금을 입력해 주세요.");
                }
                policyService.requireApplicationAllowed(reservation.getContentId());
                LocalDateTime visit = LocalDateTime.of(reservation.getReservationDate(), LocalTime.parse(reservation.getReservationTime()));
                if (!visit.isAfter(now)) throw new ResponseStatusException(HttpStatus.CONFLICT, "방문 시간이 지난 신청입니다.");
                ReservationPayment payment = reservation.getPayment();
                if (payment == null) payment = ReservationPayment.builder().reservation(reservation).build();
                payment.setAmount(request.amount());
                payment.setStatus(PaymentStatus.READY);
                payment.setMethod(null);
                payment.setPaidAt(null);
                payment.setApprovalNo(null);
                reservation.setPayment(payments.save(payment));
                reservation.setReviewStatus(ReservationReviewStatus.APPROVED);
                reservation.setStatus("결제 대기");
                reservation.setPaymentDeadline(visit.isBefore(now.plusHours(24)) ? visit : now.plusHours(24));
            }
            case REJECT, FREE -> {
                boolean free = request.decision() == ReservationReviewRequest.Decision.FREE;
                reservation.setReviewStatus(free ? ReservationReviewStatus.FREE : ReservationReviewStatus.REJECTED);
                reservation.setStatus(free ? "무료 이용 안내" : "신청 반려");
                reservation.setPaymentDeadline(null);
                if (reservation.getPayment() != null) reservation.getPayment().setStatus(PaymentStatus.CANCELED);
                if (free) {
                    ReservationPlacePolicy policy = policies.findById(reservation.getContentId()).orElseGet(ReservationPlacePolicy::new);
                    policy.setContentId(reservation.getContentId());
                    policy.setContentTypeId(reservation.getContentTypeId());
                    policy.setPlaceTitle(reservation.getPlaceTitle());
                    policy.setFeeStatus(PlaceFeeStatus.FREE);
                    policy.setReservationEnabled(false);
                    policy.setFeeNote(request.note().trim());
                    policy.setReviewedBy(adminId);
                    policy.setReviewedAt(now);
                    policies.save(policy);
                }
            }
        }
        reservation.setReviewedBy(adminId);
        reservation.setReviewedAt(now);
        reservation.setReviewNote(request.note().trim());
        return reservation;
    }
}
