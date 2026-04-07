package com.example.demo.transport.controller;

import com.example.demo.transport.dto.TransportRouteDto;
import com.example.demo.transport.repository.RouteRepository;
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
        return handleDetail(routeId, model, "transport/transportDetail");
    }

    @GetMapping("/en/transport/detail")
    public String transportDetailEn(@RequestParam(required = false) String routeId, Model model) {
        return handleDetail(routeId, model, "transport/transportDetailEn");
    }

    private String handleDetail(String routeId, Model model, String viewName) {
        if (routeId != null && !routeId.isEmpty()) {
            TransportRouteDto route = transportRouteService.getTransportRouteDetail(routeId);
            model.addAttribute("route", route);
        }
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
    @GetMapping("/api/transport/congestion")
    public int getCongestion(@RequestParam float station,
                             @RequestParam float hour,
                             @RequestParam float day) throws Exception {
        return congestionMlService.predict(station, hour, day);
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history/{id}")
    public String deleteHistory(@PathVariable Long id) {
        routeRepository.deleteById(id);
        return "ok";
    }

    @ResponseBody
    @DeleteMapping("/api/transport/history")
    public String deleteAllHistory() {
        routeRepository.deleteAll();
        return "ok";
    }
}