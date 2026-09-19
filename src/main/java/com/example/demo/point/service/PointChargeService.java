package com.example.demo.point.service;

import com.example.demo.point.dto.PointChargeVerifyRequest;
import com.example.demo.point.entity.PointCharge;
import com.example.demo.point.entity.PointChargeStatus;
import com.example.demo.point.repository.PointChargeRepository;
import com.example.demo.reservation.service.PortOneGateway;
import com.example.demo.reservation.service.PortOneOrderId;
import com.example.demo.reservation.service.PortOneSettings;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/** 마이페이지 포인트 충전. 예약금 결제(ReservationPaymentService)와 같은 포트원 테스트 채널을 그대로 쓴다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointChargeService {

    // 충전 금액대는 클라이언트가 임의로 보낼 수 있으니 서버에서도 반드시 이 목록으로만 받는다.
    private static final SortedSet<Integer> ALLOWED_AMOUNTS =
            new TreeSet<>(List.of(1000, 3000, 5000, 7000, 10000, 30000, 50000, 100000));

    private final PointChargeRepository charges;
    private final UserRepository userRepository;
    private final PortOneGateway gateway;
    private final PortOneSettings settings;

    public boolean isReady() {
        return settings.isReady();
    }

    public List<Integer> chargeTiers() {
        return List.copyOf(ALLOWED_AMOUNTS);
    }

    public List<PointCharge> history(Long userId) {
        return charges.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Map<String, Object> prepare(Long userId, int amount) {
        settings.requireReady();
        if (!ALLOWED_AMOUNTS.contains(amount)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "충전 금액을 다시 확인해 주세요.");
        }
        PointCharge charge = PointCharge.builder()
                .userId(userId)
                .amount(amount)
                .merchantUid(PortOneOrderId.create())
                .status(PointChargeStatus.READY)
                .build();
        charges.save(charge);
        return Map.of("userCode", settings.getUserCode(), "channelKey", settings.getChannelKey(),
                "merchantUid", PortOneOrderId.providerId(charge.getMerchantUid()), "amount", amount, "currency", "KRW",
                "name", amount + "P 충전");
    }

    @Transactional
    public PointCharge process(Long userId, PointChargeVerifyRequest request) {
        settings.requireReady();
        PointCharge charge = charges.findByMerchantUid(request.getMerchantUid())
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "서버에서 준비한 충전만 확인할 수 있습니다."));
        if (charge.getImpUid() != null && !charge.getImpUid().equals(request.getImpUid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 다른 결제가 연결되어 있습니다.");
        }
        if (charge.getStatus() == PointChargeStatus.PAID) return charge;

        var remote = gateway.lookup(request.getImpUid());
        gateway.validate(remote, request.getImpUid(), charge.getMerchantUid(), charge.getAmount());

        String status = remote.path("status").asText();
        if ("ready".equals(status) || "failed".equals(status)) return charge;
        if ("cancelled".equals(status) && remote.path("cancel_amount").asInt() == charge.getAmount()) {
            charge.setImpUid(request.getImpUid());
            charge.setStatus(PointChargeStatus.REFUNDED);
            return charge;
        }
        if (!"paid".equals(status) || remote.path("cancel_amount").asInt() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제가 완료되지 않았습니다.");
        }
        long paidAt = remote.path("paid_at").asLong(0);
        if (paidAt <= 0 || paidAt > Instant.now().plusSeconds(60).getEpochSecond()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 승인 시각을 확인할 수 없습니다.");
        }
        charge.setImpUid(request.getImpUid());
        charge.setStatus(PointChargeStatus.PAID);
        charge.setPaidAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(paidAt), ZoneId.systemDefault()));
        String approvalNo = remote.path("apply_num").asText("");
        charge.setApprovalNo(approvalNo.substring(0, Math.min(50, approvalNo.length())));
        userRepository.updatePoint(userId, charge.getAmount());
        return charge;
    }

    @Transactional
    public void webhook(String merchantUid, String impUid) {
        // Payload status/amount are untrusted. Only authenticated provider lookup can change state.
        PointCharge charge = charges.findByMerchantUid(merchantUid).orElse(null);
        if (charge == null) return;
        var request = new PointChargeVerifyRequest();
        request.setImpUid(impUid);
        request.setMerchantUid(merchantUid);
        process(charge.getUserId(), request);
    }
}
