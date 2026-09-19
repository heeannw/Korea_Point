package com.example.demo.reservation.service;

public class InvalidStayDatesException extends RuntimeException {
    public InvalidStayDatesException() {
        super("Checkout must be after check-in.");
    }
}
