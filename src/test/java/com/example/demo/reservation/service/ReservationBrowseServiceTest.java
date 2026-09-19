package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.*;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.ReservationPlacePolicyRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationBrowseServiceTest {
    private final TourApiReservationService api = mock(TourApiReservationService.class);
    private final ReservationPlacePolicyRepository repository = mock(ReservationPlacePolicyRepository.class);
    private final ReservationBrowseService service = new ReservationBrowseService(api, new ReservationPlacePolicyService(repository));

    private TourApiPlaceDto place(String id) {
        return TourApiPlaceDto.builder().contentId(id).title(id).build();
    }

    private ReservationPlacePolicy free(String id) {
        var policy = new ReservationPlacePolicy();
        policy.setContentId(id); policy.setFeeStatus(PlaceFeeStatus.FREE);
        return policy;
    }

    @Test
    void excludesFreeButKeepsPaidAndUnknownFees() {
        when(api.findPlaces(null, null, "ko", 1, 100)).thenReturn(List.of(place("free"), place("paid"), place("unknown")));
        var paid = new ReservationPlacePolicy(); paid.setContentId("paid"); paid.setFeeStatus(PlaceFeeStatus.PAID);
        when(repository.findAllById(any())).thenReturn(List.of(free("free"), paid));
        assertThat(service.findPlaces(null, null, "ko", 1, 30)).extracting(TourApiPlaceDto::getContentId)
                .containsExactly("paid", "unknown");
    }

    @Test
    void continuesAfterEntirelyFreePageWithSearchCategoryAndLanguage() {
        var first = IntStream.range(0, 100).mapToObj(i -> place("free" + i)).toList();
        when(api.findPlaces("museum", "culture", "en", 1, 100)).thenReturn(first);
        when(api.findPlaces("museum", "culture", "en", 2, 100)).thenReturn(List.of(place("paid")));
        when(repository.findAllById(any())).thenReturn(first.stream().map(p -> free(p.getContentId())).toList());
        assertThat(service.findPlaces("museum", "culture", "en", 1, 30)).extracting(TourApiPlaceDto::getContentId)
                .containsExactly("paid");
        verify(api).findPlaces("museum", "culture", "en", 2, 100);
    }

    @Test
    void laterPagesSkipOnlyVisiblePlacesWithoutDuplicatesOrGaps() {
        var first = new ArrayList<TourApiPlaceDto>();
        for (int i = 0; i < 100; i++) first.add(place(String.valueOf(i)));
        when(api.findPlaces(null, null, "ko", 1, 100)).thenReturn(first);
        when(api.findPlaces(null, null, "ko", 2, 100)).thenReturn(List.of(place("100"), place("101")));
        when(repository.findAllById(any())).thenReturn(IntStream.range(0, 99).mapToObj(i -> free(String.valueOf(i))).toList());
        assertThat(service.findPlaces(null, null, "ko", 1, 2)).extracting(TourApiPlaceDto::getContentId).containsExactly("99", "100");
        assertThat(service.findPlaces(null, null, "ko", 2, 2)).extracting(TourApiPlaceDto::getContentId).containsExactly("101");
    }

    @Test
    void entirelyFreeResultsAreEmpty() {
        when(api.findPlaces(null, null, "ko", 1, 100)).thenReturn(List.of(place("free")));
        when(repository.findAllById(any())).thenReturn(List.of(free("free")));
        assertThat(service.findPlaces(null, null, "ko", 1, 30)).isEmpty();
    }

}
