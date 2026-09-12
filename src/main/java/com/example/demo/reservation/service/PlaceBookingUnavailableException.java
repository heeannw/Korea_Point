package com.example.demo.reservation.service;

public class PlaceBookingUnavailableException extends RuntimeException {
    public PlaceBookingUnavailableException() {
        super("This place is not available for reservations.");
    }
}
