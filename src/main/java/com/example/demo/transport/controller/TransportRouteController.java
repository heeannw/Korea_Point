package com.example.demo.transport.controller;

import com.example.demo.config.CurrentLanguage;
import com.example.demo.config.LocalizedMessages;
import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.TransportNameTranslator;
import com.example.demo.transport.service.TransportRouteService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class TransportRouteController {

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private TransportNameTranslator nameTranslator;

    @Autowired
    private LocalizedMessages messages;

    @ResponseBody
    @GetMapping(value = "/api/kakao/search", produces = "application/json")
    public String kakaoSearch(@RequestParam String query,
                              @RequestParam(required = false) String category,
                              @RequestParam(defaultValue = "4") int size) throws Exception {
        return transportRouteService.searchKakaoPlaces(query, category, size);
    }

    @ResponseBody
    @GetMapping(value = "/api/kakao/reverse", produces = "application/json")
    public String kakaoReverse(@RequestParam double x, @RequestParam double y) throws Exception {
        return transportRouteService.reverseGeocode(x, y);
    }

    @GetMapping("/transport/search")
    public String transportSearchRedirect(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String endLat,
            @RequestParam(required = false) String endLng) {
        StringBuilder sb = new StringBuilder("redirect:" + searchPath());
        List<String> params = new ArrayList<>();
        try {
            if (start    != null && !start.isEmpty())    params.add("start="    + URLEncoder.encode(start,    StandardCharsets.UTF_8));
            if (end      != null && !end.isEmpty())      params.add("end="      + URLEncoder.encode(end,      StandardCharsets.UTF_8));
            if (endLat   != null && !endLat.isEmpty())   params.add("endLat="   + URLEncoder.encode(endLat,   StandardCharsets.UTF_8));
            if (endLng   != null && !endLng.isEmpty())   params.add("endLng="   + URLEncoder.encode(endLng,   StandardCharsets.UTF_8));
        } catch (Exception e) {
            // 인코딩 실패 시 /ko/transport/search로 이동
        }
        if (!params.isEmpty()) sb.append("?").append(String.join("&", params));
        return sb.toString();
    }

    @GetMapping("/ko/transport/search")
    public String transportSearchKo(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String endLat,
            @RequestParam(required = false) String endLng,
            HttpServletRequest request,
            Model model) {
        if (!KO_PATH.equals(searchPath())) return redirectKeepingQuery(searchPath(), request);
        return handleSearch(start, end, model, "transport/transport");
    }

    @GetMapping("/en/transport/search")
    public String transportSearchEn(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String endLat,
            @RequestParam(required = false) String endLng,
            HttpServletRequest request,
            Model model) {
        if (!EN_PATH.equals(searchPath())) return redirectKeepingQuery(searchPath(), request);
        return handleSearch(start, end, model, "transport/transportEn");
    }

    private static final String KO_PATH = "/ko/transport/search";
    private static final String EN_PATH = "/en/transport/search";

    /**
     * 검색 화면은 국문·영문 두 벌이다. 어느 쪽을 열지는 주소가 아니라 지금 언어로 정한다.
     * 일본어·중국어·불어는 아직 전용 화면이 없어 국문 화면을 쓴다.
     *
     * <p>예전에는 주소가 곧 언어였다. 그래서 일본어로 보다가 교통에 들어오면 헤더가 한국어로
     * 바뀌고, 헤더 링크가 ?lang=ko 로 만들어져 저장된 언어까지 한국어로 되돌렸다.
     * 주소와 언어가 어긋나면 맞는 쪽으로 보낸다. 검색 기록은 주소(Referer)로 언어를 가르므로
     * 둘이 늘 같아야 한다.
     */
    private static String searchPath() {
        return CurrentLanguage.EN.equals(CurrentLanguage.get()) ? EN_PATH : KO_PATH;
    }

    private static String redirectKeepingQuery(String path, HttpServletRequest request) {
        String query = request.getQueryString();
        return "redirect:" + path + (query == null || query.isBlank() ? "" : "?" + query);
    }

    // 검색 기록 저장
    @ResponseBody
    @PostMapping("/api/transport/history")
    public String saveHistory(@RequestBody Route route,
                              HttpServletRequest request) {
        String user = SecurityUtil.getCurrentUserName();
        route.setUserId(user);

        String referer = request.getHeader("Referer");
        String lang = (referer != null && referer.contains("/en")) ? "en" : "ko";
        route.setLanguage(lang);

        routeRepository.save(route);
        return "ok";
    }

    private String handleSearch(String start, String end, Model model, String viewName) {

        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());

        String user = SecurityUtil.getCurrentUserName();

        // 헤더에는 지금 언어를, 검색 기록에는 이 화면의 언어(국문/영문)를 쓴다.
        model.addAttribute("lang", CurrentLanguage.get());
        String pageLang = viewName.contains("En") ? CurrentLanguage.EN : CurrentLanguage.KO;

        // 역·노선 이름(국문) → 화면 언어. 국문 화면이면 비어 있고, 화면은 표에 없으면 국문을 쓴다.
        model.addAttribute("names", Map.of());

        model.addAttribute("recentRoutes", SecurityUtil.isLoggedIn()
                ? routeRepository.findTop10ByUserIdAndLanguageOrderBySearchedAtDesc(user, pageLang)
                : java.util.List.of());

        try {
            List<TransportRouteDto> routes;

            // 시작점 없으면 검색 안 함 (최근 검색만 표시)
            if (start == null || start.isEmpty()) {
                if (end != null && !end.isEmpty()) model.addAttribute("end", end);
                return viewName;
            }

            // 도착지 없으면 검색 안 함
            if (end == null || end.isEmpty()) {
                return viewName;
            }

            routes = transportRouteService.getTransportRoutes(start, end);
            model.addAttribute("start", start);

            System.out.println("=== 경로 조회 결과 ===");
            System.out.println("총 경로 수: " + routes.size());
            for (TransportRouteDto r : routes) {
                System.out.println("타입: " + r.getType()
                        + " | 시간: " + r.getTotalTime()
                        + "분 | 환승: " + r.getTransferCount()
                        + "회 | routeId: " + r.getRouteId());
            }
            System.out.println("====================");

            model.addAttribute("routes", routes);
            model.addAttribute("names", nameTranslator.translateAll(stepNames(routes), CurrentLanguage.get()));
            model.addAttribute("end", end);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", messages.get("transport.search.error", e.getMessage()));
        }

        return viewName;
    }

    /** 경로 카드에 보이는 역·정류장·노선 이름. */
    private static Set<String> stepNames(List<TransportRouteDto> routes) {
        Set<String> names = new LinkedHashSet<>();
        for (TransportRouteDto route : routes) {
            if (route.getSteps() == null) continue;
            route.getSteps().forEach(step -> {
                names.add(step.getFromStation());
                names.add(step.getToStation());
                names.add(step.getLine());
            });
        }
        names.remove(null);
        return names;
    }
}
