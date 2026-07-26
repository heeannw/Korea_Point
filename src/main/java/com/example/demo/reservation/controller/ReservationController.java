package com.example.demo.reservation.controller;

import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.dto.TourApiPlaceDto;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.service.ReservationService;
import com.example.demo.reservation.service.TourApiReservationService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ReservationController {

    private final TourApiReservationService tourApiReservationService;
    private final ReservationService reservationService;

    @GetMapping("/ko/reservation")
    public String reservationHome(Model model,
                                  @RequestParam(required = false) String keyword) {
        addSessionModel(model);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("hasTourApiKey", tourApiReservationService.hasServiceKey());
        addLatestReservationModel(model);
        return "reservation/reservation";
    }

    @GetMapping("/ko/reservation/new")
    public String reservationForm(@RequestParam String contentId,
                                  @RequestParam(required = false) String contentTypeId,
                                  @RequestParam(required = false) String placeTitle,
                                  Model model) {
        TourApiPlaceDto place = tourApiReservationService.findDetail(contentId, contentTypeId);
        ReservationFormDto form = new ReservationFormDto();
        form.setContentId(contentId);
        form.setContentTypeId(contentTypeId);
        form.setPlaceTitle(resolvePlaceTitle(place, placeTitle));
        form.setReservationDate(LocalDate.now());
        form.setReservationTime("10:00");

        addSessionModel(model);
        model.addAttribute("place", place);
        model.addAttribute("reservationForm", form);
        model.addAttribute("today", LocalDate.now());
        return "reservation/reservationForm";
    }

    @PostMapping("/ko/reservation/complete")
    public String reservationComplete(@Valid @ModelAttribute("reservationForm") ReservationFormDto form,
                                      BindingResult bindingResult,
                                      Model model) {
        TourApiPlaceDto place = tourApiReservationService.findDetail(form.getContentId(), form.getContentTypeId());
        if (bindingResult.hasErrors()) {
            addSessionModel(model);
            model.addAttribute("place", place);
            model.addAttribute("today", LocalDate.now());
            return "reservation/reservationForm";
        }

        TourReservation savedReservation = reservationService.create(SecurityUtil.getCurrentUserId(), form);
        return "redirect:/ko/reservation/complete/" + savedReservation.getId();
    }

    @GetMapping("/ko/reservation/complete/{reservationId}")
    public String reservationCompleteView(@PathVariable Long reservationId, Model model) {
        TourReservation reservation = reservationService.findMine(reservationId, SecurityUtil.getCurrentUserId());
        TourApiPlaceDto place = tourApiReservationService.findDetail(reservation.getContentId(), reservation.getContentTypeId());

        addSessionModel(model);
        model.addAttribute("place", place);
        model.addAttribute("reservation", reservation);
        model.addAttribute("reservationNo", reservation.getReservationNo());
        return "reservation/reservationComplete";
    }

    @GetMapping("/ko/mypage")
    public String myPage(Model model) {
        addSessionModel(model);
        model.addAttribute("today", LocalDate.now());
        List<TourReservation> reservations = reservationService.findMyReservations(SecurityUtil.getCurrentUserId());
        model.addAttribute("reservations", reservations);
        model.addAttribute("hasReservation", !reservations.isEmpty());
        if (!reservations.isEmpty()) {
            TourReservation latestReservation = reservations.get(0);
            model.addAttribute("reservation", latestReservation);
            model.addAttribute("reservationNo", latestReservation.getReservationNo());
            model.addAttribute("place", tourApiReservationService.findDetail(
                    latestReservation.getContentId(),
                    latestReservation.getContentTypeId()
            ));
        }
        return "mypage/mypage";
    }

    @GetMapping("/api/tourapi/places")
    @ResponseBody
    public ResponseEntity<List<TourApiPlaceDto>> places(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String contentTypeId,
                                                        @RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "12") int numOfRows) {
        return ResponseEntity.ok(tourApiReservationService.findPlaces(keyword, contentTypeId, pageNo, numOfRows));
    }

    @GetMapping("/api/tourapi/places/{contentId}")
    @ResponseBody
    public ResponseEntity<TourApiPlaceDto> place(@PathVariable String contentId,
                                                 @RequestParam(required = false) String contentTypeId) {
        TourApiPlaceDto place = tourApiReservationService.findDetail(contentId, contentTypeId);
        return place == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(place);
    }

    private void addSessionModel(Model model) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
    }

    private void addLatestReservationModel(Model model) {
        List<TourReservation> reservations = reservationService.findMyReservations(SecurityUtil.getCurrentUserId());
        model.addAttribute("hasReservation", !reservations.isEmpty());
        if (!reservations.isEmpty()) {
            TourReservation latestReservation = reservations.get(0);
            model.addAttribute("reservation", latestReservation);
            model.addAttribute("reservationNo", latestReservation.getReservationNo());
            model.addAttribute("place", tourApiReservationService.findDetail(
                    latestReservation.getContentId(),
                    latestReservation.getContentTypeId()
            ));
        }
    }

    private String resolvePlaceTitle(TourApiPlaceDto place, String fallbackTitle) {
        if (place != null && place.getTitle() != null && !place.getTitle().isBlank()) {
            return place.getTitle();
        }
        if (fallbackTitle != null && !fallbackTitle.isBlank()) {
            return fallbackTitle;
        }
        return "선택한 관광지";
    }
}
