package com.example.demo.reservation.controller;

import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.service.ReservationPaymentService;
import com.example.demo.reservation.service.PlaceBookingUnavailableException;
import com.example.demo.user.util.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PortOneController {
    private final ReservationPaymentService payments;

    @PostMapping("/api/reservations/{id}/payment/prepare")
    public Map<String, Object> prepare(@PathVariable Long id) {
        return payments.prepare(id, SecurityUtil.getCurrentUserId());
    }

    @PostMapping("/api/reservations/{id}/payment/verify")
    public Map<String, String> verify(@PathVariable Long id, @Valid @RequestBody ReservationPaymentRequest request) {
        var payment = payments.process(id, SecurityUtil.getCurrentUserId(), request);
        return Map.of("status", payment.getStatus().name(), "nextUrl",
                (payment.getStatus() == PaymentStatus.PAID ? "/ko/reservation/complete/" : "/ko/reservation/requests/") + id);
    }

    @PostMapping(value = "/api/portone/webhook", consumes = "application/json")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) {
        String merchant = body.path("merchant_uid").asText("");
        String imp = body.path("imp_uid").asText("");
        if (!merchant.matches("KPTEST-(?:[a-f0-9]{32}|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{9}(?:[a-f0-9]{3})?)") || !imp.matches("imp_[a-zA-Z0-9_-]{1,100}")) {
            return ResponseEntity.badRequest().build();
        }
        payments.webhook(merchant, imp);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(PlaceBookingUnavailableException.class)
    public ResponseEntity<?> unavailable() {
        return ResponseEntity.status(409).body(Map.of("code", "PLACE_NOT_BOOKABLE"));
    }
}
