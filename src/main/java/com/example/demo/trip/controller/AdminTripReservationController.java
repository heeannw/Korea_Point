package com.example.demo.trip.controller;

import com.example.demo.trip.dto.TripReservationDto;
import com.example.demo.trip.service.TripReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/api/trip/reservations")
@RequiredArgsConstructor
public class AdminTripReservationController {

    private final TripReservationService reservationService;

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(reservationService.listAll());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody TripReservationDto.StatusRequest request) {
        return reservationService.updateStatus(id, request.getStatus())
                ? ResponseEntity.ok(Map.of("status", request.getStatus()))
                : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
