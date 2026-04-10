package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.CongestionMlService;
import com.example.demo.transport.service.TransportRouteService;
import com.example.demo.user.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@Controller
public class TransportDetailController {

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private CongestionMlService congestionMlService;

    @Autowired
    private RouteRepository routeRepository;

    @GetMapping("/home")
    public String home() {
        return "home/home";
    }

    @GetMapping("/transport/detail")
    public String transportDetailRedirect(@RequestParam(required = false) String routeId) {
        return "redirect:/ko/transport/detail?routeId=" + (routeId != null ? routeId : "");
    }

    @GetMapping("/ko/transport/detail")
    public String transportDetailKo(@RequestParam(required = false) String routeId, Model model) {
        return handleDetail(routeId, model, "transport/transportDetail", "ko");
    }

    @GetMapping("/en/transport/detail")
    public String transportDetailEn(@RequestParam(required = false) String routeId, Model model) {
        return handleDetail(routeId, model, "transport/transportDetailEn", "en");
    }

    private String handleDetail(String routeId, Model model, String viewName, String lang) {

        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());

        TransportRouteDto route = null;

        if (routeId != null && !routeId.isEmpty()) {
            route = transportRouteService.getTransportRouteDetail(routeId);
        }

        int hour = java.time.LocalTime.now().getHour();
        int day = (java.time.LocalDate.now().getDayOfWeek().getValue() >= 6) ? 0 : 1;
        int line = 2;
        String station = "강남"; // 기본값

        if (route != null && route.getSteps() != null) {

            for (var step : route.getSteps()) {

                if ("subway".equals(step.getType()) && step.getLine() != null) {

                    station = step.getFromStation();

                    String lineName = step.getLine();

                    String cleanLine = lineName.replaceAll("[^가-힣0-9]", "");
                    String onlyNumber = cleanLine.replaceAll("[^0-9]", "");

                    if (!onlyNumber.isEmpty()) {
                        line = Integer.parseInt(onlyNumber);
                    } else {
                        if (cleanLine.contains("경의중앙")) line = 10;
                        else if (cleanLine.contains("분당")) line = 11;
                        else if (cleanLine.contains("공항")) line = 12;
                        else if (cleanLine.contains("신분당")) line = 14;
                        else if (cleanLine.contains("우이신설")) line = 15;
                        else line = 0;
                    }

                    break;
                }
            }
        }

        int transfer = 1;
        int time = 30;

        if (route != null) {
            transfer = route.getTransferCount();
            time = route.getTotalTime();
            model.addAttribute("route", route);
        }

        // fallback (학습 안된 노선)
        if (line == 0) {
            model.addAttribute("congestionText",
                    "en".equals(lang) ? "Moderate" : "보통");

            model.addAttribute("congestionMsg",
                    "en".equals(lang)
                            ? "No data available for this line"
                            : "혼잡도 데이터가 부족한 노선입니다");

            return viewName;
        }

        // station → 숫자 변환
        int stationNum = Math.abs(station.hashCode() % 1000);

        // ONNX 실행
        int congestion = congestionMlService.predict(hour, day, line, stationNum);

        String congestionText = "";

        if ("en".equals(lang)) {
            switch (congestion) {
                case 0: congestionText = "Comfortable"; break;
                case 1: congestionText = "Moderate"; break;
                case 2: congestionText = "Crowded"; break;
            }
        } else {
            switch (congestion) {
                case 0: congestionText = "여유"; break;
                case 1: congestionText = "보통"; break;
                case 2: congestionText = "혼잡"; break;
            }
        }

        model.addAttribute("congestionText", congestionText);

        String congestionMsg = "";

        if ("en".equals(lang)) {
            switch (congestion) {
                case 0:
                    congestionMsg = "It's a comfortable time to travel 😊";
                    break;
                case 1:
                    congestionMsg = "Passenger volume is increasing 🚶";
                    break;
                case 2:
                    congestionMsg = "It's a crowded time ⚠️ Consider another route";
                    break;
            }
        } else {
            switch (congestion) {
                case 0:
                    congestionMsg = "현재 시간대에는 비교적 쾌적하게 이동할 수 있습니다 😊";
                    break;
                case 1:
                    congestionMsg = "이용객이 점점 늘어나는 시간대입니다 🚶";
                    break;
                case 2:
                    congestionMsg = "혼잡한 시간대입니다 ⚠️ 다른 경로를 고려해보세요";
                    break;
            }
        }

        model.addAttribute("congestionMsg", congestionMsg);

        return viewName;
    }

    @ResponseBody
    @GetMapping("/api/transport/route/{routeId}")
    public TransportRouteDto getRouteJson(@PathVariable String routeId) {
        return transportRouteService.getTransportRouteDetail(routeId);
    }

    @ResponseBody
    @GetMapping("/api/transport/subway")
    public String transportSubwayArrival(
            @RequestParam(required = false) String station) throws Exception {

        if (station == null || station.isEmpty()) {
            return "{\"realtimeArrivalList\":[]}";
        }

        station = station.replaceAll("\\s+", "").trim();
        if (station.endsWith("역")) {
            station = station.substring(0, station.length() - 1);
        }

        System.out.println("요청 역: " + station);
        return callApi(station);
    }

    private String callApi(String station) throws Exception {
        String key = "746f74584172696e37395277457a71";

        StringBuilder urlBuilder = new StringBuilder("http://swopenapi.seoul.go.kr/api/subway");
        urlBuilder.append("/").append(key);
        urlBuilder.append("/json/realtimeStationArrival/1/5/");
        urlBuilder.append(URLEncoder.encode(station, "UTF-8"));

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader rd = (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300)
                ? new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))
                : new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) sb.append(line);
        rd.close();
        conn.disconnect();
        return sb.toString();
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history/{id}")
    public String deleteHistory(@PathVariable Long id) {

        Route route = routeRepository.findById(id).orElse(null);

        if (route == null) return "fail";

        String user = SecurityUtil.isLoggedIn()
                ? SecurityUtil.getCurrentUserName()
                : "guest";

        // 내 것만 삭제 가능
        if (!user.equals(route.getUserId())) {
            return "forbidden";
        }

        routeRepository.deleteById(id);
        return "ok";
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history")
    public String deleteMyHistory() {

        String user = SecurityUtil.isLoggedIn()
                ? SecurityUtil.getCurrentUserName()
                : "guest";

        routeRepository.deleteByUserId(user);

        return "ok";
    }
}