package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.PlaceBookingPolicyDto;
import com.example.demo.reservation.dto.TourApiPlaceDto;
import com.example.demo.reservation.entity.PlaceFeeStatus;
import com.example.demo.reservation.entity.ReservationPlacePolicy;
import com.example.demo.reservation.repository.ReservationPlacePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationPlacePolicyService {
    private final ReservationPlacePolicyRepository repository;

    public PlaceBookingPolicyDto policy(String contentId) {
        return describe(contentId == null ? null : repository.findById(contentId).orElse(null));
    }

    public Map<String, PlaceBookingPolicyDto> policies(Collection<String> ids) {
        Map<String, ReservationPlacePolicy> stored = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(ReservationPlacePolicy::getContentId, Function.identity()));
        Map<String, PlaceBookingPolicyDto> result = new LinkedHashMap<>();
        ids.forEach(id -> result.put(id, describe(stored.get(id))));
        return result;
    }

    public List<TourApiPlaceDto> decorate(List<TourApiPlaceDto> places) {
        Map<String, PlaceBookingPolicyDto> policies = policies(places.stream().map(TourApiPlaceDto::getContentId).toList());
        places.forEach(place -> place.setBookingPolicy(policies.get(place.getContentId())));
        return places;
    }

    public TourApiPlaceDto decorate(TourApiPlaceDto place) {
        if (place != null) place.setBookingPolicy(policy(place.getContentId()));
        return place;
    }

    // The shared row lock is held through the surrounding create/payment transaction.
    // An administrator's update cannot commit halfway through a new payment.
    @Transactional
    public void requireBookable(String contentId) {
        if (contentId == null || contentId.isBlank() || !repository.findForBooking(contentId)
                .map(ReservationPlacePolicy::isBookable).orElse(false)) {
            throw new PlaceBookingUnavailableException();
        }
    }

    @Transactional
    public void requireApplicationAllowed(String contentId) {
        if (contentId == null || contentId.isBlank()) throw new PlaceBookingUnavailableException();
        ReservationPlacePolicy policy = repository.findForBooking(contentId).orElse(null);
        if (policy != null && (policy.getFeeStatus() == PlaceFeeStatus.FREE
                || (policy.getFeeStatus() == PlaceFeeStatus.PAID && !policy.isReservationEnabled()))) {
            throw new PlaceBookingUnavailableException();
        }
    }

    private PlaceBookingPolicyDto describe(ReservationPlacePolicy policy) {
        boolean bookable = policy == null || policy.getFeeStatus() == PlaceFeeStatus.UNKNOWN || policy.isBookable();
        return new PlaceBookingPolicyDto(policy == null ? PlaceFeeStatus.UNKNOWN : policy.getFeeStatus(),
                bookable, null);
    }
}
