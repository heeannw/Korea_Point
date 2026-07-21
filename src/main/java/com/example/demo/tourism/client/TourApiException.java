package com.example.demo.tourism.client;

public class TourApiException extends RuntimeException {
    public TourApiException(String message) {
        super(message);
    }

    public TourApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
