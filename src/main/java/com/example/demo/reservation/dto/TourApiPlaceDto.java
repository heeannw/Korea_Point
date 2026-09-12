package com.example.demo.reservation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TourApiPlaceDto {

    @lombok.Setter
    private PlaceBookingPolicyDto bookingPolicy;

    private final String contentId;
    private final String contentTypeId;
    private final String title;
    private final String address;
    private final String tel;
    private final String imageUrl;
    private final String mapX;
    private final String mapY;
    private final String overview;
    private final Map<String, String> operatingInformation;

}
