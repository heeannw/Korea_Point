package com.example.demo.reservation.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class ReservationFormDto {

    private String lang = "ko";

    @NotBlank
    private String contentId;

    private String contentTypeId;

    @NotBlank
    private String placeTitle;

    @NotNull
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @FutureOrPresent
    private LocalDate reservationDate;

    @NotBlank
    @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d")
    private String reservationTime;

    @Min(1)
    @Max(10)
    private int peopleCount = 1;

    private String memo;
}
