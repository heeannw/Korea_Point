package com.example.demo.trip.dto;

import com.example.demo.trip.entity.ReservationStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TripReservationDto {

    @Data
    public static class CreateRequest {
        @NotNull private Long tripPlanId;
        @NotBlank @Size(max = 200) private String placeName;
        @NotNull @FutureOrPresent private LocalDate visitDate;
        @NotNull private LocalTime visitTime;
        @Min(1) @Max(30) private int partySize;
        @Size(max = 500) private String requestNote;
    }

    @Data
    public static class Response {
        private Long id;
        private Long tripPlanId;
        private String tripTitle;
        private String placeName;
        private LocalDate visitDate;
        private LocalTime visitTime;
        private int partySize;
        private String requestNote;
        private ReservationStatus status;
        private LocalDateTime createdAt;
    }
}
