package com.example.demo.reservation.dto;

import jakarta.validation.constraints.*;

public record ReservationReviewRequest(@NotNull Decision decision,
        @Min(1) @Max(10000000) Integer amount,
        @NotBlank @Size(max = 1000) String note) {
    public enum Decision { APPROVE, REJECT, FREE }
}
