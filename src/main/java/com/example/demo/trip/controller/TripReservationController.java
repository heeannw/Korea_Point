package com.example.demo.trip.controller;

import com.example.demo.trip.dto.TripReservationDto;
import com.example.demo.trip.service.TripReservationService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trip/reservation")
@RequiredArgsConstructor
public class TripReservationController {

    private final TripReservationService reservationService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody TripReservationDto.CreateRequest request) {
        Long id = reservationService.create(currentUserId(), request);
        return ResponseEntity.ok(Map.of("reservationId", id, "status", "REQUESTED"));
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(reservationService.list(currentUserId()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        return reservationService.cancel(currentUserId(), id)
                ? ResponseEntity.ok(Map.of("status", "CANCELLED"))
                : ResponseEntity.notFound().build();
    }

    private String currentUserId() {
        return String.valueOf(SecurityUtil.getCurrentUserId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
