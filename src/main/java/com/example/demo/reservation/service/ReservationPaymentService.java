package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.event.ReservationConfirmedEvent;
import com.example.demo.reservation.repository.ReservationPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationPaymentService {
    private final ReservationService reservationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationPlacePolicyService placePolicyService;
    private final PortOneGateway gateway;
    private final PortOneSettings settings;
    private final ReservationPaymentRepository payments;

    public boolean isReady() { return settings.isReady(); }

    @Transactional
    public Map<String, Object> prepare(Long reservationId, Long userId) {
        settings.requireReady();
        var reservation = reservationService.findMineForUpdate(reservationId, userId);
        if (!reservation.isPayable()) throw new PlaceBookingUnavailableException();
        placePolicyService.requireApplicationAllowed(reservation.getContentId());
        var payment = reservation.getPayment();
        if (payment.getMerchantUid() == null) payment.setMerchantUid(PortOneOrderId.create());
        return Map.of("userCode", settings.getUserCode(), "channelKey", settings.getChannelKey(),
                "merchantUid", PortOneOrderId.providerId(payment.getMerchantUid()), "amount", payment.getAmount(), "currency", "KRW",
                "name", reservation.getPlaceTitle());
    }

    @Transactional
    public ReservationPayment process(Long reservationId, Long userId, ReservationPaymentRequest request) {
        settings.requireReady();
        var reservation = reservationService.findMineForUpdate(reservationId, userId);
        var payment = reservation.getPayment();
        if (payment == null || payment.getMerchantUid() == null
                || !PortOneOrderId.providerId(payment.getMerchantUid()).equals(PortOneOrderId.providerId(request.getMerchantUid()))
                || request.getImpUid() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "서버에서 준비한 결제만 확인할 수 있습니다.");
        }
        var remote = gateway.lookup(request.getImpUid());
        gateway.validate(remote, request.getImpUid(), payment.getMerchantUid(), payment.getAmount());
        if (payment.getImpUid() != null && !payment.getImpUid().equals(request.getImpUid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 결제가 연결되어 있습니다.");
        }
        String status = remote.path("status").asText();
        if ("ready".equals(status) || "failed".equals(status)) return payment;
        if ("cancelled".equals(status) && remote.path("cancel_amount").asInt() == payment.getAmount()) {
            payment.setImpUid(request.getImpUid());
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(LocalDateTime.now());
            reservation.setStatus("예약 취소");
            return payment;
        }
        if (!"paid".equals(status) || remote.path("cancel_amount").asInt() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제가 완료되지 않았습니다.");
        }
        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FORFEITED) return payment;
        long paidAt = remote.path("paid_at").asLong(0);
        if (paidAt <= 0 || paidAt > Instant.now().plusSeconds(60).getEpochSecond()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 승인 시각을 확인할 수 없습니다.");
        }
        var paidTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(paidAt), ZoneId.systemDefault());
        boolean eligible = reservation.getReviewStatus() == ReservationReviewStatus.APPROVED
                && !"예약 취소".equals(reservation.getStatus())
                && (payment.getStatus() == PaymentStatus.READY || payment.getStatus() == PaymentStatus.FAILED)
                && reservation.getPaymentDeadline() != null && paidTime.isBefore(reservation.getPaymentDeadline());
        try { placePolicyService.requireApplicationAllowed(reservation.getContentId()); }
        catch (PlaceBookingUnavailableException ex) { eligible = false; }
        if (!eligible) {
            gateway.cancel(request.getImpUid(), payment.getMerchantUid(), payment.getAmount());
            payment.setImpUid(request.getImpUid());
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(LocalDateTime.now());
            reservation.setStatus("예약 취소");
            return payment;
        }
        payment.setImpUid(request.getImpUid());
        payment.setMethod(switch (remote.path("emb_pg_provider").asText()) {
            case "kakaopay" -> PaymentMethod.KAKAO_PAY;
            case "naverpay" -> PaymentMethod.NAVER_PAY;
            default -> "point".equals(remote.path("pay_method").asText()) ? PaymentMethod.POINT : PaymentMethod.CARD;
        });
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidTime);
        payment.setApprovalNo(remote.path("apply_num").asText("").substring(0, Math.min(50, remote.path("apply_num").asText("").length())));
        reservation.setStatus("예약 완료");
        eventPublisher.publishEvent(new ReservationConfirmedEvent(reservation.getUserId(), reservation.getReservationNo(),
                reservation.getPlaceTitle(), reservation.getReservationDate(), reservation.getReservationTime(),
                reservation.getPeopleCount(), payment.getAmount()));
        return payment;
    }

    @Transactional
    public void webhook(String merchantUid, String impUid) {
        // Payload status/amount are untrusted. Only authenticated provider lookup can change state.
        var route = payments.findRouteByMerchantUid(merchantUid).orElse(null);
        if (route == null) return;
        var request = new ReservationPaymentRequest();
        request.setImpUid(impUid); request.setMerchantUid(merchantUid); request.setPaymentMethod(PaymentMethod.CARD);
        process(route.getReservationId(), route.getUserId(), request);
    }
}
