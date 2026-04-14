package com.example.demo.transport.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.entity.Route;
import com.example.demo.transport.repository.RouteRepository;
import com.example.demo.transport.service.CongestionMlService;
import com.example.demo.transport.service.TransportRouteService;
import com.example.demo.user.util.SecurityUtil;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SEOUL_API_KEY = "746f74584172696e37395277457a71";

    @Autowired
    private TransportRouteService transportRouteService;

    @Autowired
    private CongestionMlService congestionMlService;

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
        String station = "\uAC15\uB0A8";
        int stationCode = 0;
        String direction = "\uB0B4\uC120";

        if (route != null && route.getSteps() != null) {
            for (var step : route.getSteps()) {
                if ("subway".equals(step.getType()) && step.getLine() != null) {
                    if (step.getFromStation() != null && !step.getFromStation().isBlank()) {
                        station = step.getFromStation();
                    }

                    String lineName = step.getLine();
                    String cleanLine = lineName.replaceAll("[^\\uAC00-\\uD7A30-9]", "");
                    String onlyNumber = cleanLine.replaceAll("[^0-9]", "");

                    if (!onlyNumber.isEmpty()) {
                        line = Integer.parseInt(onlyNumber);
                    } else {
                        if (cleanLine.contains("\uACBD\uC758\uC911\uC559")) line = 10;
                        else if (cleanLine.contains("\uBD84\uB2F9")) line = 11;
                        else if (cleanLine.contains("\uACF5\uD56D")) line = 12;
                        else if (cleanLine.contains("\uC2E0\uBD84\uB2F9")) line = 14;
                        else if (cleanLine.contains("\uC6B0\uC774\uC2E0\uC124")) line = 15;
                        else line = 0;
                    }

                    break;
                }
            }
        }

        if (route != null) {
            model.addAttribute("route", route);
        }

        if (line == 0) {
            model.addAttribute("congestionText", "en".equals(lang) ? "Moderate" : "\uBCF4\uD1B5");
            model.addAttribute(
                    "congestionMsg",
                    "en".equals(lang)
                            ? "No data available for this line"
                            : "\uD63C\uC7A1\uB3C4 \uB370\uC774\uD130\uAC00 \uBD80\uC871\uD55C \uB178\uC120\uC785\uB2C8\uB2E4"
            );
            return viewName;
        }

        stationCode = resolveStationCode(station, line, stationCode);
        direction = resolveDirection(station, line, direction);
        int congestion = congestionMlService.predict(hour, day, station, line, stationCode, direction);

        String congestionText;
        if ("en".equals(lang)) {
            switch (congestion) {
                case 0: congestionText = "Comfortable"; break;
                case 1: congestionText = "Moderate"; break;
                case 2: congestionText = "Crowded"; break;
                default: congestionText = "Moderate"; break;
            }
        } else {
            switch (congestion) {
                case 0: congestionText = "\uC5EC\uC720"; break;
                case 1: congestionText = "\uBCF4\uD1B5"; break;
                case 2: congestionText = "\uD63C\uC7A1"; break;
                default: congestionText = "\uBCF4\uD1B5"; break;
            }
        }
        model.addAttribute("congestionText", congestionText);

        String congestionMsg;
        if ("en".equals(lang)) {
            switch (congestion) {
                case 0:
                    congestionMsg = "It's a comfortable time to travel.";
                    break;
                case 1:
                    congestionMsg = "Passenger volume is increasing.";
                    break;
                case 2:
                    congestionMsg = "It's a crowded time. Consider another route.";
                    break;
                default:
                    congestionMsg = "Passenger volume is average.";
                    break;
            }
        } else {
            switch (congestion) {
                case 0:
                    congestionMsg = "\uD604\uC7AC \uC2DC\uAC04\uB300\uB294 \uBE44\uAD50\uC801 \uCFFC\uC801\uD558\uAC8C \uC774\uB3D9\uD560 \uC218 \uC788\uC2B5\uB2C8\uB2E4.";
                    break;
                case 1:
                    congestionMsg = "\uC774\uC6A9\uAC1D\uC774 \uC810\uC810 \uB298\uC5B4\uB098\uB294 \uC2DC\uAC04\uB300\uC785\uB2C8\uB2E4.";
                    break;
                case 2:
                    congestionMsg = "\uD63C\uC7A1\uD55C \uC2DC\uAC04\uB300\uC785\uB2C8\uB2E4. \uB2E4\uB978 \uACBD\uB85C\uB3C4 \uACE0\uB824\uD574\uBCF4\uC138\uC694.";
                    break;
                default:
                    congestionMsg = "\uD3C9\uADE0\uC801\uC778 \uD63C\uC7A1\uB3C4\uC785\uB2C8\uB2E4.";
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
        if (station.endsWith("\uC5ED")) {
            station = station.substring(0, station.length() - 1);
        }

        System.out.println("station request: " + station);
        return callApi(station);
    }

    private String callApi(String station) throws Exception {
        StringBuilder urlBuilder = new StringBuilder("http://swopenapi.seoul.go.kr/api/subway");
        urlBuilder.append("/").append(SEOUL_API_KEY);
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

    private int resolveStationCode(String station, int line, int fallbackStationCode) {
        try {
            StringBuilder urlBuilder = new StringBuilder("http://openapi.seoul.go.kr:8088");
            urlBuilder.append("/").append(SEOUL_API_KEY);
            urlBuilder.append("/json/SearchInfoBySubwayNameService/1/10/");
            urlBuilder.append(URLEncoder.encode(station, "UTF-8"));

            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader rd = (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300)
                    ? new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))
                    : new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));

            StringBuilder sb = new StringBuilder();
            String lineText;
            while ((lineText = rd.readLine()) != null) sb.append(lineText);
            rd.close();
            conn.disconnect();

            JsonNode root = OBJECT_MAPPER.readTree(sb.toString());
            JsonNode rows = root.path("SearchInfoBySubwayNameService").path("row");

            if (!rows.isArray() || rows.isEmpty()) {
                return fallbackStationCode;
            }

            for (JsonNode row : rows) {
                String lineNum = row.path("LINE_NUM").asText("");
                if (!lineNameMatches(lineNum, line)) {
                    continue;
                }

                int stationCode = row.path("STATION_CD").asInt(0);
                if (stationCode != 0) {
                    return stationCode;
                }
            }

            JsonNode first = rows.get(0);
            return first != null ? first.path("STATION_CD").asInt(fallbackStationCode) : fallbackStationCode;
        } catch (Exception e) {
            System.out.println("station code lookup failed: " + e.getMessage());
            return fallbackStationCode;
        }
    }

    private String resolveDirection(String station, int line, String fallbackDirection) {
        try {
            String response = callApi(station);
            JsonNode root = OBJECT_MAPPER.readTree(response);
            JsonNode arrivals = root.path("realtimeArrivalList");

            if (!arrivals.isArray() || arrivals.isEmpty()) {
                return fallbackDirection;
            }

            String targetSubwayId = mapLineToSubwayId(line);

            for (JsonNode arrival : arrivals) {
                if (targetSubwayId != null && !targetSubwayId.equals(arrival.path("subwayId").asText())) {
                    continue;
                }

                String updnLine = arrival.path("updnLine").asText("").trim();
                if (!updnLine.isEmpty()) {
                    return updnLine;
                }
            }

            for (JsonNode arrival : arrivals) {
                String updnLine = arrival.path("updnLine").asText("").trim();
                if (!updnLine.isEmpty()) {
                    return updnLine;
                }
            }
        } catch (Exception e) {
            System.out.println("direction lookup failed: " + e.getMessage());
        }

        return fallbackDirection;
    }

    private String mapLineToSubwayId(int line) {
        return switch (line) {
            case 1 -> "1001";
            case 2 -> "1002";
            case 3 -> "1003";
            case 4 -> "1004";
            case 5 -> "1005";
            case 6 -> "1006";
            case 7 -> "1007";
            case 8 -> "1008";
            case 9 -> "1009";
            default -> null;
        };
    }

    private boolean lineNameMatches(String lineName, int line) {
        String normalized = lineName == null ? "" : lineName.replaceAll("\\s+", "");

        return switch (line) {
            case 1, 2, 3, 4, 5, 6, 7, 8, 9 -> normalized.contains(line + "\uD638\uC120");
            case 10 -> normalized.contains("\uACBD\uC758\uC911\uC559");
            case 11 -> normalized.contains("\uC218\uC778\uBD84\uB2F9") || normalized.contains("\uBD84\uB2F9");
            case 12 -> normalized.contains("\uACF5\uD56D");
            case 14 -> normalized.contains("\uC2E0\uBD84\uB2F9");
            case 15 -> normalized.contains("\uC6B0\uC774\uC2E0\uC124");
            default -> false;
        };
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history/{id}")
    public String deleteHistory(@PathVariable Long id) {
        Route route = routeRepository.findById(id).orElse(null);
        if (route == null) return "fail";

        String user = SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserName() : "guest";
        if (!user.equals(route.getUserId())) {
            return "forbidden";
        }

        routeRepository.deleteById(id);
        return "ok";
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history")
    public String deleteMyHistory() {
        String user = SecurityUtil.isLoggedIn() ? SecurityUtil.getCurrentUserName() : "guest";
        routeRepository.deleteByUserId(user);
        return "ok";
    }
}
