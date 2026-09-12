package com.example.demo.admin.controller;

import com.example.demo.reservation.dto.ReservationReviewRequest;
import com.example.demo.reservation.entity.*;
import com.example.demo.reservation.repository.TourReservationRepository;
import com.example.demo.reservation.service.ReservationReviewService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController
@RequestMapping("/admin/api/reservation-requests")
@RequiredArgsConstructor
public class AdminReservationReviewController {
    private final TourReservationRepository reservations;
    private final ReservationReviewService reviews;

    public record Item(Long id, Long userId, String reservationNo, String contentId, String placeTitle,
            LocalDate reservationDate, String reservationTime, int peopleCount, String memo,
            String state, Integer amount, String reviewNote, LocalDateTime paymentDeadline) {
        static Item of(TourReservation r) {
            return new Item(r.getId(), r.getUserId(), r.getReservationNo(), r.getContentId(), r.getPlaceTitle(),
                    r.getReservationDate(), r.getReservationTime(), r.getPeopleCount(), r.getMemo(),
                    r.getApplicationStatus(), r.getPayment() == null ? null : r.getPayment().getAmount(),
                    r.getReviewNote(), r.getPaymentDeadline());
        }
    }

    @GetMapping
    public Page<Item> list(@RequestParam(defaultValue = "true") boolean pendingOnly,
                           @RequestParam(defaultValue = "0") int page) {
        return reservations.findReviewQueue(pendingOnly, ReservationReviewStatus.PENDING, PaymentStatus.PAID,
                PageRequest.of(Math.max(0, page), 20, Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by("id"))))
                .map(Item::of);
    }

    @PostMapping("/{id}/review")
    public Item review(@PathVariable Long id, @Valid @RequestBody ReservationReviewRequest request) {
        return Item.of(reviews.review(id, SecurityUtil.getCurrentUserId(), request));
    }

    @ExceptionHandler({com.example.demo.reservation.service.PlaceBookingUnavailableException.class,
            org.springframework.dao.OptimisticLockingFailureException.class,
            org.springframework.dao.DataIntegrityViolationException.class})
    public org.springframework.http.ResponseEntity<?> conflict() {
        return org.springframework.http.ResponseEntity.status(409).body(java.util.Map.of(
                "message", "장소 설정이 변경되어 처리할 수 없습니다. 새로고침 후 확인해 주세요."));
    }
}
