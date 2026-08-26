package com.example.demo.trip.dto;

import lombok.Data;
import java.util.List;

@Data
public class TripReoptimizeRequest {
    private TripRecommendRequest request;
    private TripRecommendResponse current;
    private List<String> completedPlaceNames;
    private List<String> reservedPlaceNames;
}
