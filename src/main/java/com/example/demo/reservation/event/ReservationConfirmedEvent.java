package com.example.demo.reservation.event;

import java.time.LocalDate;

public record ReservationConfirmedEvent(
        Long userId,
        String reservationNo,
        String placeTitle,
        LocalDate reservationDate,
        String reservationTime,
        int peopleCount,
        int depositAmount
) {
}
