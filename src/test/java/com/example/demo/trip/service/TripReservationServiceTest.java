package com.example.demo.trip.service;

import com.example.demo.trip.dto.TripReservationDto;
import com.example.demo.trip.entity.TripPlan;
import com.example.demo.trip.entity.TripPlanDay;
import com.example.demo.trip.entity.TripPlanSlot;
import com.example.demo.trip.entity.TripReservation;
import com.example.demo.trip.repository.TripPlanRepository;
import com.example.demo.trip.repository.TripReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripReservationServiceTest {

    private final TripReservationRepository reservationRepository = mock(TripReservationRepository.class);
    private final TripPlanRepository tripPlanRepository = mock(TripPlanRepository.class);
    private final TripReservationService service = new TripReservationService(reservationRepository, tripPlanRepository);

    @Test
    void createsRequestOnlyForPlaceInUsersSavedPlan() {
        TripPlan plan = planWithPlace("롯데월드");
        TripReservationDto.CreateRequest request = request("롯데월드");
        when(tripPlanRepository.findWithDaysAndSlots(3L, "42")).thenReturn(Optional.of(plan));
        when(reservationRepository.save(any())).thenAnswer(invocation -> {
            TripReservation saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        assertThat(service.create("42", request)).isEqualTo(8L);
        verify(reservationRepository).save(any(TripReservation.class));
    }

    @Test
    void rejectsPlaceThatIsNotInSavedPlan() {
        when(tripPlanRepository.findWithDaysAndSlots(3L, "42"))
                .thenReturn(Optional.of(planWithPlace("롯데월드")));

        assertThatThrownBy(() -> service.create("42", request("다른 장소")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일정에 포함된 장소");
    }

    @Test
    void rejectsPlanOwnedByAnotherUser() {
        when(tripPlanRepository.findWithDaysAndSlots(3L, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("42", request("롯데월드")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일정을 찾을 수 없습니다");
    }

    private TripPlan planWithPlace(String placeName) {
        TripPlanSlot slot = TripPlanSlot.builder().placeName(placeName).build();
        TripPlanDay day = TripPlanDay.builder().day(1).slots(List.of(slot)).build();
        return TripPlan.builder().id(3L).title("서울 여행").days(List.of(day)).build();
    }

    private TripReservationDto.CreateRequest request(String placeName) {
        TripReservationDto.CreateRequest request = new TripReservationDto.CreateRequest();
        request.setTripPlanId(3L);
        request.setPlaceName(placeName);
        request.setVisitDate(LocalDate.now().plusDays(1));
        request.setVisitTime(LocalTime.NOON);
        request.setPartySize(2);
        return request;
    }
}
