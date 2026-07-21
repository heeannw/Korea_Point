package com.example.demo.trip.controller;

import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.service.TripPlanService;
import com.example.demo.user.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripPlanControllerTest {

    private final TripPlanService tripPlanService = mock(TripPlanService.class);
    private final TripPlanController controller = new TripPlanController(tripPlanService);

    @Test
    void saveUsesAuthenticatedDatabaseUserId() {
        TripPlanDto.SaveRequest request = new TripPlanDto.SaveRequest();
        when(tripPlanService.save("42", request)).thenReturn(7L);

        try (MockedStatic<SecurityUtil> security = loggedInAs(42L)) {
            ResponseEntity<?> response = controller.save(request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(tripPlanService).save("42", request);
        }
    }

    @Test
    void listUsesAuthenticatedDatabaseUserId() {
        List<TripPlanDto.Summary> plans = List.of(new TripPlanDto.Summary());
        when(tripPlanService.listByUser("42")).thenReturn(plans);

        try (MockedStatic<SecurityUtil> security = loggedInAs(42L)) {
            ResponseEntity<?> response = controller.list();

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(plans);
        }
    }

    @Test
    void detailDoesNotExposeAnotherUsersPlan() {
        when(tripPlanService.getDetail("42", 9L)).thenReturn(null);

        try (MockedStatic<SecurityUtil> security = loggedInAs(42L)) {
            ResponseEntity<?> response = controller.detail(9L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            verify(tripPlanService).getDetail("42", 9L);
        }
    }

    @Test
    void deleteUsesAuthenticatedDatabaseUserId() {
        when(tripPlanService.delete("42", 9L)).thenReturn(true);

        try (MockedStatic<SecurityUtil> security = loggedInAs(42L)) {
            ResponseEntity<?> response = controller.delete(9L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(tripPlanService).delete("42", 9L);
        }
    }

    @Test
    void unauthenticatedRequestIsRejected() {
        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::isLoggedIn).thenReturn(false);

            assertThat(controller.list().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private MockedStatic<SecurityUtil> loggedInAs(Long userId) {
        MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class);
        security.when(SecurityUtil::isLoggedIn).thenReturn(true);
        security.when(SecurityUtil::getCurrentUserId).thenReturn(userId);
        return security;
    }
}
