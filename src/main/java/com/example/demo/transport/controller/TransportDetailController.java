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

   @GetMapping("/home")
   public String home(){
       return "home/home";
   }

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

    // route JSON 반환 API
    @ResponseBody
    @GetMapping("/api/transport/route/{routeId}")
    public TransportRouteDto getRouteJson(@PathVariable String routeId) {
        return transportRouteService.getTransportRouteDetail(routeId);
    }

    //지하철 실시간 도착정보 API (최종 완성)
    @ResponseBody
    @GetMapping("/api/transport/subway")
    public String transportSubwayArrival(
            @RequestParam(required = false) String station) throws Exception {

        if (station == null || station.isEmpty()) {
            return "{\"realtimeArrivalList\":[]}";
        }

        //핵심: 여기서만 처리 (완전 안전 버전)
        station = station.replaceAll("\\s+", "")   // 공백 제거
                .trim();

        System.out.println("요청 역: " + station);

        return callApi(station);
    }

    //API 호출 (정리된 버전)
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

        while ((line = rd.readLine()) != null) {
            sb.append(line);
        }

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