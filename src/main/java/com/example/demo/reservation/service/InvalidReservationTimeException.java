package com.example.demo.reservation.service;

public class InvalidReservationTimeException extends RuntimeException {
    public InvalidReservationTimeException() {
        super("Select a future visit date and time.");
    }
}
