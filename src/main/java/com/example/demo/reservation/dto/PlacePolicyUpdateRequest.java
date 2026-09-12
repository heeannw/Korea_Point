package com.example.demo.reservation.dto;

import com.example.demo.reservation.entity.PlaceFeeStatus;
import jakarta.validation.constraints.*;

public record PlacePolicyUpdateRequest(
        @NotNull PlaceFeeStatus feeStatus,
        @NotNull Boolean reservationEnabled,
        @NotBlank @Size(max = 1000) String feeNote,
        Long version) {}
