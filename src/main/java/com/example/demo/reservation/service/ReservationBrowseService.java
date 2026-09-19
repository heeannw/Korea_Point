package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.TourApiPlaceDto;
import com.example.demo.reservation.entity.PlaceFeeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationBrowseService {
    private final TourApiReservationService tourApi;
    private final ReservationPlacePolicyService policies;

    public List<TourApiPlaceDto> findPlaces(String keyword, String contentTypeId, String lang,
                                            int pageNo, int numOfRows) {
        int size = Math.max(1, Math.min(numOfRows, 100));
        long skip = (long) (Math.max(pageNo, 1) - 1) * size;
        int sourceSize = 100;
        List<TourApiPlaceDto> result = new ArrayList<>();
        for (int sourcePage = 1; ; sourcePage++) {
            List<TourApiPlaceDto> source = tourApi.findPlaces(keyword, contentTypeId, lang, sourcePage, sourceSize);
            for (TourApiPlaceDto place : policies.decorate(source)) {
                if (place.getBookingPolicy().feeStatus() == PlaceFeeStatus.FREE) continue;
                if (skip > 0) {
                    skip--;
                } else {
                    result.add(place);
                    if (result.size() == size) return result;
                }
            }
            // Use the unfiltered source length: an all-free page is not the end of the list.
            if (source.size() < sourceSize) return result;
        }
    }
}
