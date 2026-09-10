package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.*;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.ReservationPlacePolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReservationPlaceAdminServiceTest {
    private final ReservationPlacePolicyRepository repository = mock(ReservationPlacePolicyRepository.class);
    private final TourApiReservationService tourApi = mock(TourApiReservationService.class);
    private final ReservationPlaceAdminService service = new ReservationPlaceAdminService(repository, tourApi);

    @Test
    void rejectsFreePlaceWithReservationsEnabled() {
        assertThatThrownBy(() -> service.update("123", new PlacePolicyUpdateRequest(PlaceFeeStatus.FREE, true, "무료 확인", null), 7L))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(repository, tourApi);
    }

    @Test
    void rejectsStaleAdminEdits() {
        var stored = new ReservationPlacePolicy(); stored.setVersion(2L);
        when(repository.findById("123")).thenReturn(Optional.of(stored));
        assertThatThrownBy(() -> service.update("123", new PlacePolicyUpdateRequest(PlaceFeeStatus.PAID, true, "공식 요금 확인", 1L), 7L))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void validatesPlaceAndRecordsReviewer() {
        when(tourApi.findDetail("123", null, "ko")).thenReturn(TourApiPlaceDto.builder().contentId("123").contentTypeId("14").title("박물관").build());
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.update("123", new PlacePolicyUpdateRequest(PlaceFeeStatus.PAID, true, "  성인 10,000원 공식 안내 확인  ", null), 7L);
        assertThat(saved.isBookable()).isTrue();
        assertThat(saved.getPlaceTitle()).isEqualTo("박물관");
        assertThat(saved.getFeeNote()).isEqualTo("성인 10,000원 공식 안내 확인");
        assertThat(saved.getReviewedBy()).isEqualTo(7L);
        assertThat(saved.getReviewedAt()).isNotNull();
    }

    @Test
    void unavailableTourApiCannotAuthorizeAnInventedPlace() {
        assertThatThrownBy(() -> service.update("123", new PlacePolicyUpdateRequest(PlaceFeeStatus.PAID, true, "요금 확인", null), 7L))
                .isInstanceOf(ResponseStatusException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
