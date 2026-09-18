package com.example.demo.transport.controller;

import com.example.demo.config.CurrentLanguage;
import com.example.demo.config.LocalizedMessages;
import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.BusanBusArrivalService;
import com.example.demo.transport.service.CongestionMlService;
import com.example.demo.transport.service.TransportNameTranslator;
import com.example.demo.transport.service.TransportRouteService;
import com.example.demo.user.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

@Controller
public class TransportDetailController {

    @Value("${SEOUL_SUBWAY_API_KEY:}")
    private String seoulApiKey;

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private TransportNameTranslator nameTranslator;

    @Autowired
    private LocalizedMessages messages;

    @Autowired
    private CongestionMlService congestionMlService;

    @Autowired
    private BusanBusArrivalService busanBusArrivalService;

    @Autowired
    private RouteRepository routeRepository;

    @GetMapping("/transport/detail")
    public String transportDetailRedirect(@RequestParam(required = false) String routeId) {
        return "redirect:/" + CurrentLanguage.get() + "/transport/detail?routeId=" + (routeId != null ? routeId : "");
    }

    /**
     * 상세 화면. 주소 앞의 언어가 무엇이든 화면은 지금 언어로 그린다.
     *
     * <p>예전에는 /ko 주소면 lang 이 없을 때 한국어로 떴다. 검색 화면의 경로 링크가
     * /ko/transport/detail?routeId=… 로만 걸려 있어서, 일본어로 보던 사람도 한국어 상세를 보고
     * 헤더가 저장된 언어를 한국어로 되돌렸다.
     */
    @GetMapping({"/ko/transport/detail", "/en/transport/detail",
            "/ja/transport/detail", "/zh/transport/detail", "/fr/transport/detail"})
    public String transportDetail(@RequestParam(required = false) String routeId, Model model) {
        String lang = CurrentLanguage.get();
        String viewName = CurrentLanguage.KO.equals(lang)
                ? "transport/transportDetail"
                : "transport/transportDetailTranslated";
        return handleDetail(routeId, model, viewName, lang);
    }

    private String handleDetail(String routeId, Model model, String viewName, String lang) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        model.addAttribute("lang", lang);

        TransportRouteDto route = null;
        if (routeId != null && !routeId.isEmpty()) {
            route = transportRouteService.getTransportRouteDetail(routeId);
        }
        // 경로의 역·정류장·노선 이름을 미리 옮겨 화면에 넘긴다(외국어 화면이 먼저 이 표를 본다).
        model.addAttribute("names", route == null ? Map.of() : nameTranslator.translateAll(routeNames(route), lang));

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

        boolean showCongestion = route != null
                && route.getSteps() != null
                && route.getSteps().stream().anyMatch(step -> "subway".equals(step.getType()));
        model.addAttribute("showCongestion", showCongestion);

        if (!showCongestion) {
            return viewName;
        }

        if (line == 0) {
            model.addAttribute("congestionText", "ko".equals(lang) ? "보통" : "Moderate");
            model.addAttribute(
                    "congestionMsg",
                    "ko".equals(lang)
                            ? "혼잡도 데이터가 부족한 노선입니다."
                            : "No data available for this line"
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
        if (!"ko".equals(lang)) {
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
        if (!"ko".equals(lang)) {
            // 칩 글자(congestionText)는 화면 스크립트가 막대 길이를 정하는 데 쓰므로 영문 그대로 둔다.
            // 안내문만 화면 언어로. 예전에는 일·중·불도 영문이었다.
            congestionMsg = messages.get("transport.congestion.msg."
                    + (congestion >= 0 && congestion <= 3 ? String.valueOf(congestion) : "none"));
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

    /** 상세 화면에 보이는 이름들: 출발·도착, 각 구간의 역·정류장·노선. */
    private static java.util.Set<String> routeNames(TransportRouteDto route) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        names.add(route.getStart());
        names.add(route.getEnd());
        if (route.getSteps() != null) {
            route.getSteps().forEach(step -> {
                names.add(step.getFromStation());
                names.add(step.getToStation());
                names.add(step.getLine());
            });
        }
        names.remove(null);
        return names;
    }

    @ResponseBody
    @GetMapping("/api/transport/route/{routeId}")
    public TransportRouteDto getRouteJson(@PathVariable String routeId) {
        return transportRouteService.getTransportRouteDetail(routeId);
    }

    /**
     * 상세 화면이 역·정류장·노선 이름을 하나씩 옮겨 달라고 부른다.
     * 역 이름 사전과 기억을 먼저 보고, 없을 때만 번역기를 부른다(TransportNameTranslator).
     * 못 옮기면 받은 글자를 그대로 돌려주고, 화면이 자체 규칙으로 옮긴다.
     */
    @ResponseBody
    @PostMapping("/api/transport/translate")
    public ResponseEntity<?> translateTransportText(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        if (text.isBlank()) {
            return ResponseEntity.ok(Map.of("translatedText", ""));
        }
        String target = body.getOrDefault("target", "en");
        return ResponseEntity.ok(Map.of("translatedText", nameTranslator.translate(text, target)));
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
