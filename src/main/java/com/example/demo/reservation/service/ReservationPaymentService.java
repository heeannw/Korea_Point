package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.entity.ReservationPayment;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.event.ReservationConfirmedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationPaymentService {

    private final ReservationService reservationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReservationPayment process(Long reservationId, Long userId, ReservationPaymentRequest request) {
        TourReservation reservation = reservationService.findMine(reservationId, userId);
        ReservationPayment payment = requirePayment(reservation);

        if (payment.getStatus() == PaymentStatus.PAID) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.CANCELED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.FORFEITED) {
            throw new IllegalStateException("취소되었거나 환불된 예약은 다시 결제할 수 없습니다.");
        }

        payment.setMethod(request.getPaymentMethod());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setApprovalNo(createApprovalNo());
        reservation.setStatus("예약 완료");
        eventPublisher.publishEvent(new ReservationConfirmedEvent(
                reservation.getUserId(),
                reservation.getReservationNo(),
                reservation.getPlaceTitle(),
                reservation.getReservationDate(),
                reservation.getReservationTime(),
                reservation.getPeopleCount(),
                payment.getAmount()));
        return payment;
    }

    private ReservationPayment requirePayment(TourReservation reservation) {
        if (reservation.getPayment() == null) {
            throw new IllegalStateException("이 예약의 결제 정보를 찾을 수 없습니다.");
        }
        return reservation.getPayment();
    }

    private String createApprovalNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "KPAY-" + date + "-" + suffix;
    }
}
