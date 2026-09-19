package com.example.demo.point.controller;

import com.example.demo.point.dto.PointChargeRequest;
import com.example.demo.point.dto.PointChargeVerifyRequest;
import com.example.demo.point.service.PointChargeService;
import com.example.demo.user.util.SecurityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PointChargeController {

    private final PointChargeService pointChargeService;

    @PostMapping("/api/points/charge/prepare")
    public Map<String, Object> prepare(@Valid @RequestBody PointChargeRequest request) {
        return pointChargeService.prepare(SecurityUtil.getCurrentUserId(), request.getAmount());
    }

    @PostMapping("/api/points/charge/verify")
    public Map<String, String> verify(@Valid @RequestBody PointChargeVerifyRequest request) {
        var charge = pointChargeService.process(SecurityUtil.getCurrentUserId(), request);
        return Map.of("status", charge.getStatus().name());
    }

    @PostMapping(value = "/api/points/webhook", consumes = "application/json")
    public ResponseEntity<Void> webhook(@RequestBody JsonNode body) {
        String merchant = body.path("merchant_uid").asText("");
        String imp = body.path("imp_uid").asText("");
        if (!merchant.matches("KPTEST-[a-f0-9]{32}") || !imp.matches("imp_[a-zA-Z0-9_-]{1,100}")) {
            return ResponseEntity.badRequest().build();
        }
        pointChargeService.webhook(merchant, imp);
        return ResponseEntity.ok().build();
    }
}
