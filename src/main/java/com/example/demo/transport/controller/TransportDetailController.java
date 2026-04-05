package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.service.CongestionMlService;
import com.example.demo.transport.service.TransportRouteService;
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

    // 상세 페이지
    @GetMapping("/transport/detail")
    public String transportDetail(@RequestParam(required = false) String routeId,
                                  Model model) {
        if (routeId != null && !routeId.isEmpty()) {
            TransportRouteDto route = transportRouteService.getTransportRouteDetail(routeId);
            model.addAttribute("route", route);
        }
        return "transport/transportDetail";
    }

    // route JSON 반환 API (JS에서 fetch로 호출)
    @ResponseBody
    @GetMapping("/api/transport/route/{routeId}")
    public TransportRouteDto getRouteJson(@PathVariable String routeId) {
        return transportRouteService.getTransportRouteDetail(routeId);
    }

    // 지하철 실시간 도착정보 API
    @ResponseBody
    @GetMapping("/api/transport/subway")
    public String transportSubwayArrival(
            @RequestParam(required = false) String station) throws Exception {

        if (station == null || station.isEmpty()) {
            return "{\"realtimeArrivalList\":[]}";
        }

        System.out.println("요청 역: " + station);
        String result = callApi(station);

        if (result.contains("해당하는 데이터가 없습니다")) {
            System.out.println("재시도: " + station + "역");
            result = callApi(station + "역");
        }

        return result;
    }

    private String callApi(String station) throws Exception {
        station = station.replace("역", "").trim();

        String key = "436243644472696e36316879424961";
        StringBuilder urlBuilder = new StringBuilder("http://openapi.seoul.go.kr:8088");
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

    // 혼잡도 예측 API (0=여유, 1=보통, 2=혼잡)
    @ResponseBody
    @GetMapping("/api/transport/congestion")
    public int getCongestion(@RequestParam float station,
                             @RequestParam float hour,
                             @RequestParam float day) throws Exception {
        return congestionMlService.predict(station, hour, day);
    }
}