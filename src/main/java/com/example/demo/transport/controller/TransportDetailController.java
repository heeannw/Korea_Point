package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.BusanBusArrivalService;
import com.example.demo.transport.service.CongestionMlService;
import com.example.demo.transport.service.TransportRouteService;
import com.example.demo.user.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@Controller
public class TransportDetailController {

    @Value("${SEOUL_SUBWAY_API_KEY:}")
    private String seoulApiKey;

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private CongestionMlService congestionMlService;

    @Autowired
    private BusanBusArrivalService busanBusArrivalService;

    @Autowired
    private RouteRepository routeRepository;

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
        int fallbackLine = 2;
        Integer stationId = null;
        Integer odsayLine = null;
        String city = "seoul";
        String station = "강남";
        String rawLineName = null;

        if (route != null && route.getSteps() != null) {
            for (var step : route.getSteps()) {
                if ("subway".equals(step.getType()) && step.getLine() != null) {
                    if (step.getFromStation() != null && !step.getFromStation().isBlank()) {
                        station = step.getFromStation();
                    }
                    stationId = step.getFromStationId();
                    odsayLine = step.getSubwayCode();

                    rawLineName = step.getLine();
                    city = rawLineName.contains("부산") ? "busan" : "seoul";
                    String cleanLine = rawLineName.replaceAll("[^가-힣0-9]", "");
                    String onlyNumber = cleanLine.replaceAll("[^0-9]", "");

                    if (!onlyNumber.isEmpty()) {
                        fallbackLine = Integer.parseInt(onlyNumber);
                    } else {
                        if (cleanLine.contains("경의중앙")) fallbackLine = 10;
                        else if (cleanLine.contains("분당")) fallbackLine = 11;
                        else if (cleanLine.contains("공항")) fallbackLine = 12;
                        else if (cleanLine.contains("신분당")) fallbackLine = 14;
                        else if (cleanLine.contains("우이신설")) fallbackLine = 15;
                        else fallbackLine = 0;
                    }

                    line = fallbackLine > 0 ? fallbackLine : (odsayLine != null ? odsayLine : 0);
                    break;
                }
            }
        }

        if (route != null) {
            model.addAttribute("route", route);
        }

        if (line == 0) {
            model.addAttribute("congestionText", "en".equals(lang) ? "Moderate" : "보통");
            model.addAttribute(
                    "congestionMsg",
                    "en".equals(lang)
                            ? "No data available for this line"
                            : "혼잡도 데이터가 부족한 노선입니다."
            );
            return viewName;
        }

        System.out.println("=== congestion debug start ===");
        System.out.println("city = " + city);
        System.out.println("station raw = " + station);
        System.out.println("station odsay id = " + stationId);
        System.out.println("line raw = " + rawLineName);
        System.out.println("line odsay code = " + odsayLine + ", fallback line = " + fallbackLine);
        System.out.println("hour = " + hour + ", day = " + day + ", line = " + line);
        if (station != null) {
            System.out.println("station codepoints = " +
                    station.chars()
                            .mapToObj(c -> String.format("U+%04X", c))
                            .toList());
        }
        if (rawLineName != null) {
            System.out.println("line codepoints = " +
                    rawLineName.chars()
                            .mapToObj(c -> String.format("U+%04X", c))
                            .toList());
        }
        System.out.println("=== congestion debug end ===");

        int congestion = congestionMlService.predict(city, hour, day, station, stationId, line);

        String congestionText;
        if ("en".equals(lang)) {
            switch (congestion) {
                case 0: congestionText = "Normal"; break;
                case 1: congestionText = "Caution"; break;
                case 2: congestionText = "Crowded"; break;
                case 3: congestionText = "Severe"; break;
                default: congestionText = "Unavailable"; break;
            }
        } else {
            switch (congestion) {
                case 0: congestionText = "보통"; break;
                case 1: congestionText = "주의"; break;
                case 2: congestionText = "혼잡"; break;
                case 3: congestionText = "심각"; break;
                default: congestionText = "데이터 없음"; break;
            }
        }
        model.addAttribute("congestionText", congestionText);

        String congestionMsg;
        if ("en".equals(lang)) {
            switch (congestion) {
                case 0:
                    congestionMsg = "Passenger volume is within the normal range.";
                    break;
                case 1:
                    congestionMsg = "Passenger volume is increasing. Please stay alert.";
                    break;
                case 2:
                    congestionMsg = "This is a crowded time. Consider another route.";
                    break;
                case 3:
                    congestionMsg = "Severe congestion is expected. Consider delaying or changing routes.";
                    break;
                default:
                    congestionMsg = "Congestion data is currently unavailable.";
                    break;
            }
        } else {
            switch (congestion) {
                case 0:
                    congestionMsg = "현재 시간대는 보통 수준의 혼잡도입니다.";
                    break;
                case 1:
                    congestionMsg = "이용객이 늘어나는 주의 단계입니다.";
                    break;
                case 2:
                    congestionMsg = "혼잡한 시간대입니다. 다른 경로도 고려해보세요.";
                    break;
                case 3:
                    congestionMsg = "혼잡도가 심각한 시간대입니다. 이동 시간이나 경로 변경을 권장합니다.";
                    break;
                default:
                    congestionMsg = "현재 혼잡도 데이터를 확인할 수 없습니다.";
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
    public String transportSubwayArrival(@RequestParam(required = false) String station) throws Exception {
        if (station == null || station.isEmpty()) {
            return "{\"realtimeArrivalList\":[]}";
        }

        station = station.replaceAll("\\s+", "").trim();
        if (station.endsWith("역")) {
            station = station.substring(0, station.length() - 1);
        }

        System.out.println("station request: " + station);
        return callApi(station);
    }

    private String callApi(String station) throws Exception {
        if (seoulApiKey == null || seoulApiKey.isBlank()) {
            return "{\"realtimeArrivalList\":[],\"message\":\"Subway API key is not configured\"}";
        }
        StringBuilder urlBuilder = new StringBuilder("http://swopenapi.seoul.go.kr/api/subway");
        urlBuilder.append("/").append(seoulApiKey);
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
    @GetMapping("/api/transport/busan/bus-arrival")
    public String busanBusArrival(
            @RequestParam(required = false) String stopName,
            @RequestParam(required = false) String busNo,
            @RequestParam(required = false) String bstopid,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng
    ) {
        return busanBusArrivalService.getArrivalJson(stopName, busNo, bstopid, lat, lng);
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history/{id}")
    public String deleteHistory(@PathVariable Long id) {
        Route route = routeRepository.findById(id).orElse(null);
        if (route == null) return "fail";

        String user = SecurityUtil.getCurrentUserName();
        if (!user.equals(route.getUserId())) {
            return "forbidden";
        }

        routeRepository.deleteById(id);
        return "ok";
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history")
    public String deleteMyHistory() {
        String user = SecurityUtil.getCurrentUserName();
        routeRepository.deleteByUserId(user);
        return "ok";
    }
}
