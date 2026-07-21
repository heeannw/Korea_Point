package com.example.demo.reservation.controller;

import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.dto.TourApiPlaceDto;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ReservationController {

    private final TourApiReservationService tourApiReservationService;

    @GetMapping("/ko/reservation")
    public String reservationHome(Model model,
                                  @RequestParam(required = false) String keyword) {
        addSessionModel(model);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("hasTourApiKey", tourApiReservationService.hasServiceKey());
        return "reservation/reservation";
    }

    @GetMapping("/ko/reservation/new")
    public String reservationForm(@RequestParam String contentId,
                                  @RequestParam(required = false) String contentTypeId,
                                  Model model) {
        TourApiPlaceDto place = tourApiReservationService.findDetail(contentId, contentTypeId);
        ReservationFormDto form = new ReservationFormDto();
        form.setContentId(contentId);
        form.setContentTypeId(contentTypeId);
        form.setPlaceTitle(place != null && !place.getTitle().isBlank() ? place.getTitle() : "선택한 관광지");
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

        addSessionModel(model);
        model.addAttribute("place", place);
        model.addAttribute("reservation", form);
        model.addAttribute("reservationNo", createReservationNo(form));
        return "reservation/reservationComplete";
    }

    @GetMapping("/ko/mypage")
    public String myPage(Model model) {
        addSessionModel(model);
        model.addAttribute("today", LocalDate.now());
        return "mypage/mypage";
    }

    @GetMapping("/api/tourapi/places")
    @ResponseBody
    public ResponseEntity<List<TourApiPlaceDto>> places(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "12") int numOfRows) {
        return ResponseEntity.ok(tourApiReservationService.findPlaces(keyword, pageNo, numOfRows));
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

    private String createReservationNo(ReservationFormDto form) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int seed = Math.abs((form.getContentId() + form.getReservationDate() + form.getReservationTime()).hashCode());
        return "KP-" + date + "-" + String.format("%04d", seed % 10000);
    }
}
