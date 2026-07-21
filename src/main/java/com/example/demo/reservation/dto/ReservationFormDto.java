package com.example.demo.reservation.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReservationFormDto {

    @NotBlank
    private String contentId;

    private String contentTypeId;

    @NotBlank
    private String placeTitle;

    @NotNull
    @FutureOrPresent
    private LocalDate reservationDate;

    @NotBlank
    private String reservationTime;

    @Min(1)
    @Max(10)
    private int peopleCount = 1;

    private String memo;
}
