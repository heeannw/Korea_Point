package com.example.demo.reservation.dto;

import com.example.demo.reservation.entity.PlaceFeeStatus;

public record PlaceBookingPolicyDto(PlaceFeeStatus feeStatus, boolean reservationAvailable, Integer depositPerPerson) {}
