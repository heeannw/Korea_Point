package com.example.demo.reservation.controller;

import com.example.demo.reservation.dto.ReservationFormDto;
import com.example.demo.reservation.dto.ReservationPaymentRequest;
import com.example.demo.reservation.dto.TourApiPlaceDto;
import com.example.demo.reservation.entity.PaymentMethod;
import com.example.demo.reservation.entity.PaymentStatus;
import com.example.demo.reservation.entity.TourReservation;
import com.example.demo.reservation.service.ReservationService;
import com.example.demo.reservation.service.ReservationPaymentService;
import com.example.demo.reservation.service.TourApiReservationService;
import com.example.demo.community.repository.CategoryRepository;
import com.example.demo.community.repository.PostRepository;
import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.service.TripPlanService;
import com.example.demo.tourism.dto.SavedPlaceDto;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.tourism.service.SavedPlaceService;
import com.example.demo.user.entity.OwnerRequest;
import com.example.demo.user.entity.User;
import com.example.demo.user.service.OwnerRequestService;
import com.example.demo.user.service.UserService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class ReservationController {

    private final TourApiReservationService tourApiReservationService;
    private final ReservationService reservationService;
    private final ReservationPaymentService reservationPaymentService;
    private final UserService userService;
    private final OwnerRequestService ownerRequestService;
    private final TripPlanService tripPlanService;
    private final SavedPlaceService savedPlaceService;
    private final TourPlaceI18n tourPlaceI18n;
    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;
    private final com.example.demo.reservation.service.ReservationPlacePolicyService placePolicyService;

    @GetMapping("/ko/reservation")
    public String reservationHome(Model model,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "ko") String lang) {
        lang = normalizeReservationLang(lang);
        applyReservationLocale(lang);
        addSessionModel(model);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("lang", lang);
        model.addAttribute("hasTourApiKey", tourApiReservationService.hasServiceKey());
        addLatestReservationModel(model);
        return "reservation/reservation";
    }

    @GetMapping("/ko/reservation/new")
    public String reservationForm(@RequestParam String contentId,
                                  @RequestParam(required = false) String contentTypeId,
                                  @RequestParam(required = false) String placeTitle,
                                  @RequestParam(defaultValue = "ko") String lang,
                                  Model model) {
        lang = normalizeReservationLang(lang);
        applyReservationLocale(lang);
        placePolicyService.requireApplicationAllowed(contentId);
        TourApiPlaceDto place = tourApiReservationService.findDetail(contentId, contentTypeId, lang);
        if (place == null) throw new com.example.demo.reservation.service.PlaceBookingUnavailableException();
        ReservationFormDto form = new ReservationFormDto();
        form.setLang(lang);
        form.setContentId(contentId);
        form.setContentTypeId(contentTypeId);
        form.setPlaceTitle(resolvePlaceTitle(place, placeTitle));
        form.setReservationDate(LocalDate.now().plusDays(1));
        form.setReservationTime("10:00");

        addSessionModel(model);
        model.addAttribute("place", place);
        model.addAttribute("reservationForm", form);
        model.addAttribute("lang", lang);
        model.addAttribute("today", LocalDate.now());
        return "reservation/reservationForm";
    }

    @PostMapping("/ko/reservation/complete")
    public String reservationComplete(@Valid @ModelAttribute("reservationForm") ReservationFormDto form,
                                      BindingResult bindingResult,
                                      Model model) {
        form.setLang(normalizeReservationLang(form.getLang()));
        applyReservationLocale(form.getLang());
        placePolicyService.requireApplicationAllowed(form.getContentId());
        TourApiPlaceDto place = tourApiReservationService.findDetail(
                form.getContentId(), form.getContentTypeId(), form.getLang());
        if (place == null) throw new com.example.demo.reservation.service.PlaceBookingUnavailableException();
        form.setPlaceTitle(place.getTitle());
        form.setContentTypeId(place.getContentTypeId());
        if (bindingResult.hasErrors()) {
            applyReservationLocale(form.getLang());
            addSessionModel(model);
            model.addAttribute("place", place);
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("lang", form.getLang());
            return "reservation/reservationForm";
        }

        try {
            TourReservation savedReservation = reservationService.create(SecurityUtil.getCurrentUserId(), form);
            return "redirect:/ko/reservation/requests/" + savedReservation.getId();
        } catch (com.example.demo.reservation.service.InvalidReservationTimeException ex) {
            bindingResult.rejectValue("reservationTime", "reservation.form.futureVisit");
            addSessionModel(model);
            model.addAttribute("place", place);
            model.addAttribute("today", LocalDate.now());
            model.addAttribute("lang", form.getLang());
            return "reservation/reservationForm";
        }
    }

    @GetMapping("/ko/reservation/payment/{reservationId}")
    public String reservationPayment(@PathVariable Long reservationId, Model model) {
        TourReservation reservation = reservationService.findMine(reservationId, SecurityUtil.getCurrentUserId());
        if (reservation.getPayment() == null) {
            return "redirect:/ko/reservation/complete/" + reservationId;
        }
        if (reservation.getPayment().getStatus() == PaymentStatus.PAID) {
            return "redirect:/ko/reservation/complete/" + reservationId;
        }
        applyReservationLocale(reservationLanguage(reservation));
        if (!reservation.isPayable()) return "redirect:/ko/reservation/requests/" + reservationId;
        placePolicyService.requireApplicationAllowed(reservation.getContentId());
        addSessionModel(model);
        model.addAttribute("reservation", reservation);
        model.addAttribute("payment", reservation.getPayment());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("paymentRequest", new ReservationPaymentRequest());
        model.addAttribute("lang", reservationLanguage(reservation));
        applyReservationLocale(reservationLanguage(reservation));
        return "reservation/reservationPayment";
    }

    @PostMapping("/ko/reservation/payment/{reservationId}")
    public String processReservationPayment(@PathVariable Long reservationId,
                                            @Valid @ModelAttribute("paymentRequest") ReservationPaymentRequest request,
                                            BindingResult bindingResult,
                                            Model model) {
        TourReservation reservation = reservationService.findMine(reservationId, SecurityUtil.getCurrentUserId());
        applyReservationLocale(reservationLanguage(reservation));
        if (reservation.getPayment() == null || reservation.getPayment().getStatus() != PaymentStatus.PAID) {
            if (!reservation.isPayable()) throw new com.example.demo.reservation.service.PlaceBookingUnavailableException();
            placePolicyService.requireApplicationAllowed(reservation.getContentId());
        }
        if (bindingResult.hasErrors()) {
            applyReservationLocale(reservationLanguage(reservation));
            addSessionModel(model);
            model.addAttribute("reservation", reservation);
            model.addAttribute("payment", reservation.getPayment());
            model.addAttribute("paymentMethods", PaymentMethod.values());
            model.addAttribute("lang", reservationLanguage(reservation));
            return "reservation/reservationPayment";
        }

        reservationPaymentService.process(reservationId, SecurityUtil.getCurrentUserId(), request);
        return "redirect:/ko/reservation/complete/" + reservationId;
    }

    @GetMapping("/ko/reservation/complete/{reservationId}")
    public String reservationCompleteView(@PathVariable Long reservationId, Model model) {
        TourReservation reservation = reservationService.findMine(reservationId, SecurityUtil.getCurrentUserId());
        if (!"CONFIRMED".equals(reservation.getApplicationStatus())) {
            return "redirect:/ko/reservation/requests/" + reservationId;
        }
        TourApiPlaceDto place = tourApiReservationService.findDetail(
                reservation.getContentId(), reservation.getContentTypeId(), reservationLanguage(reservation));

        addSessionModel(model);
        model.addAttribute("place", place);
        model.addAttribute("reservation", reservation);
        model.addAttribute("reservationNo", reservation.getReservationNo());
        model.addAttribute("payment", reservation.getPayment());
        model.addAttribute("lang", reservationLanguage(reservation));
        applyReservationLocale(reservationLanguage(reservation));
        return "reservation/reservationComplete";
    }

    @GetMapping("/ko/reservation/requests/{reservationId}")
    public String reservationRequestView(@PathVariable Long reservationId, Model model) {
        TourReservation reservation = reservationService.findMine(reservationId, SecurityUtil.getCurrentUserId());
        if ("CONFIRMED".equals(reservation.getApplicationStatus())) {
            return "redirect:/ko/reservation/complete/" + reservationId;
        }
        applyReservationLocale(reservationLanguage(reservation));
        addSessionModel(model);
        model.addAttribute("lang", reservationLanguage(reservation));
        model.addAttribute("reservation", reservation);
        return "reservation/reservationRequest";
    }

    @PatchMapping("/api/reservations/{reservationId}")
    @ResponseBody
    public ResponseEntity<?> updateReservation(@PathVariable Long reservationId,
                                               @Valid @RequestBody ReservationFormDto form,
                                               BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body("예약 정보를 확인해 주세요.");
        }
        try {
            TourReservation reservation = reservationService.update(reservationId, SecurityUtil.getCurrentUserId(), form);
            return ResponseEntity.ok(reservation);
        } catch (com.example.demo.reservation.service.InvalidReservationTimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_VISIT_TIME"));
        }
    }

    @PatchMapping("/api/reservations/{reservationId}/cancel")
    @ResponseBody
    public ResponseEntity<?> cancelReservation(@PathVariable Long reservationId) {
        TourReservation reservation = reservationService.cancel(reservationId, SecurityUtil.getCurrentUserId());
        return ResponseEntity.ok(reservation);
    }

    @GetMapping("/ko/mypage")
    public String myPage(Model model) {
        addSessionModel(model);
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.findById(userId);
        Optional<OwnerRequest> latestOwnerRequest = ownerRequestService.findLatestByUserId(userId);
        List<TourReservation> reservations = reservationService.findMyReservations(userId);
        String lang = normalizeReservationLang(LocaleContextHolder.getLocale().getLanguage());
        List<TourReservation> activeReservations = reservations.stream()
                .filter(reservation -> !"예약 취소".equals(reservation.getStatus()))
                .toList();
        List<TripPlanDto.Summary> tripPlans = tripPlanService.listByUser(String.valueOf(userId));
        List<SavedPlaceDto> savedPlaces = savedPlaceService.list(userId, null);
        long postCount = postRepository.countByUser_IdAndDeletedAtIsNull(userId);
        long promoPostCount = postRepository.countByUser_IdAndCategory_NameAndDeletedAtIsNull(userId, "홍보");
        Long promoCategoryId = categoryRepository.findByName("홍보")
                .map(category -> category.getId())
                .orElse(null);

        model.addAttribute("today", LocalDate.now());
        model.addAttribute("user", user);
        model.addAttribute("hasProvidedEmail", userService.hasProvidedEmail(user));
        model.addAttribute("loginProviderName", userService.loginProviderName(user));
        model.addAttribute("isOwner", "OWNER".equals(user.getRole()));
        model.addAttribute("ownerRequest", latestOwnerRequest.orElse(null));
        model.addAttribute("ownerStatus", latestOwnerRequest
                .map(request -> request.getStatus().name())
                .orElse("NONE"));
        model.addAttribute("hasPendingOwnerRequest", latestOwnerRequest
                .map(request -> "PENDING".equals(request.getStatus().name()))
                .orElse(false));
        model.addAttribute("tripPlans", tripPlans);
        model.addAttribute("hasTripPlans", !tripPlans.isEmpty());
        model.addAttribute("tripPlanCount", tripPlans.size());
        model.addAttribute("reservationCount", reservations.size());
        model.addAttribute("savedPlaces", savedPlaces);
        model.addAttribute("savedPlaceCount", savedPlaces.size());
        model.addAttribute("postCount", postCount);
        model.addAttribute("promoPostCount", promoPostCount);
        model.addAttribute("promoCategoryId", promoCategoryId);
        model.addAttribute("reservations", reservations);
        model.addAttribute("reservationTitleById", localizedReservationTitles(reservations, lang));
        model.addAttribute("activeReservations", activeReservations);
        model.addAttribute("hasReservation", !reservations.isEmpty());
        model.addAttribute("hasActiveReservation", !activeReservations.isEmpty());
        if (!reservations.isEmpty()) {
            TourReservation latestReservation = reservations.get(0);
            model.addAttribute("reservation", latestReservation);
            model.addAttribute("reservationNo", latestReservation.getReservationNo());
            model.addAttribute("place", tourApiReservationService.findDetail(
                    latestReservation.getContentId(),
                    latestReservation.getContentTypeId(),
                    reservationLanguage(latestReservation)
            ));
        }
        return "mypage/mypage";
    }

    private Map<Long, String> localizedReservationTitles(List<TourReservation> reservations, String lang) {
        Map<Long, String> titles = new LinkedHashMap<>();
        for (TourReservation reservation : reservations) {
            titles.put(reservation.getId(), tourPlaceI18n.translateTitle(
                    lang, reservation.getContentId(), reservation.getPlaceTitle()));
        }
        return titles;
    }

    @PostMapping("/ko/mypage/owner-application")
    public String applyOwner(@RequestParam String storeName,
                             @RequestParam String ownerName,
                             @RequestParam String startDate,
                             @RequestParam String businessRegistrationNumber,
                             @RequestParam MultipartFile businessRegistrationFile,
                             @RequestParam MultipartFile businessLicenseFile,
                             @RequestParam MultipartFile menuFile,
                             @RequestParam(defaultValue = "ko") String lang,
                             RedirectAttributes redirectAttributes) {
        try {
            ownerRequestService.apply(
                    SecurityUtil.getCurrentUserId(),
                    storeName,
                    ownerName,
                    startDate,
                    businessRegistrationNumber,
                    businessRegistrationFile,
                    businessLicenseFile,
                    menuFile
            );
            redirectAttributes.addFlashAttribute("ownerRequestMessage", "사업자 전환 신청이 접수됐어요. 검토 후 권한이 변경됩니다.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("ownerRequestError", ex.getMessage());
        }
        return "redirect:/ko/mypage?lang=" + normalizeReservationLang(lang);
    }

    @GetMapping("/api/tourapi/places")
    @ResponseBody
    public ResponseEntity<List<TourApiPlaceDto>> places(@RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String contentTypeId,
                                                        @RequestParam(defaultValue = "ko") String lang,
                                                        @RequestParam(defaultValue = "1") int pageNo,
                                                        @RequestParam(defaultValue = "12") int numOfRows) {
        return ResponseEntity.ok(placePolicyService.decorate(tourApiReservationService.findPlaces(
                keyword, contentTypeId, normalizeReservationLang(lang), pageNo, Math.min(numOfRows, 100))));
    }

    @GetMapping("/api/tourapi/places/{contentId}")
    @ResponseBody
    public ResponseEntity<TourApiPlaceDto> place(@PathVariable String contentId,
                                                 @RequestParam(required = false) String contentTypeId,
                                                 @RequestParam(defaultValue = "ko") String lang) {
        TourApiPlaceDto place = tourApiReservationService.findDetail(
                contentId, contentTypeId, normalizeReservationLang(lang));
        return place == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(placePolicyService.decorate(place));
    }

    @GetMapping("/api/reservation-place-policies")
    @ResponseBody
    public ResponseEntity<?> placePolicies(@RequestParam List<String> ids) {
        if (ids.size() > 100 || ids.stream().anyMatch(id -> !id.matches("[0-9]{1,30}"))) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(placePolicyService.policies(ids));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(com.example.demo.reservation.service.PlaceBookingUnavailableException.class)
    public org.springframework.web.servlet.ModelAndView bookingUnavailable(jakarta.servlet.http.HttpServletRequest request) {
        org.springframework.web.servlet.ModelAndView view;
        if (request.getRequestURI().startsWith("/api/")) {
            view = new org.springframework.web.servlet.ModelAndView(
                    new org.springframework.web.servlet.view.json.MappingJackson2JsonView(),
                    Map.of("code", "PLACE_NOT_BOOKABLE"));
        } else {
            view = new org.springframework.web.servlet.ModelAndView("reservation/reservationUnavailable");
            view.addObject("lang", normalizeReservationLang(LocaleContextHolder.getLocale().getLanguage()));
        }
        view.setStatus(org.springframework.http.HttpStatus.CONFLICT);
        return view;
    }

    private void addSessionModel(Model model) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        if (SecurityUtil.isLoggedIn()) {
            model.addAttribute("currentUserId", SecurityUtil.getCurrentUserId());
            model.addAttribute("currentUserName", userService.findById(SecurityUtil.getCurrentUserId()).getName());
            return;
        }
        model.addAttribute("currentUserId", null);
        model.addAttribute("currentUserName", null);
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
                    latestReservation.getContentTypeId(),
                    reservationLanguage(latestReservation)
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

    private String reservationLanguage(TourReservation reservation) {
        return normalizeReservationLang(reservation.getLanguage());
    }

    private String normalizeReservationLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return "ko";
        }
        String normalized = lang.trim().toLowerCase().replace('_', '-');
        if (normalized.startsWith("en")) return "en";
        if (normalized.startsWith("ja") || normalized.startsWith("jp")) return "ja";
        if (normalized.startsWith("zh")) return "zh-CN";
        if (normalized.startsWith("fr")) return "fr";
        return "ko";
    }

    private void applyReservationLocale(String lang) {
        String normalized = normalizeReservationLang(lang);
        Locale locale = switch (normalized) {
            case "en" -> Locale.ENGLISH;
            case "ja" -> Locale.JAPANESE;
            case "zh-CN" -> Locale.SIMPLIFIED_CHINESE;
            case "fr" -> Locale.FRENCH;
            default -> Locale.KOREAN;
        };
        LocaleContextHolder.setLocale(locale);
    }
}
