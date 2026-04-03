package com.example.demo.transport.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

@Controller
public class DetailController {

    @GetMapping("/transport/detail")
    public String detail() {
        return "transport/transportDetail";
    }

    @ResponseBody
    @GetMapping("/api/subway")
    public String subway(@RequestParam(required = false) String station) throws Exception {

        // 파라미터 없으면 기본값
        if (station == null || station.equals("")) {
            station = "서울";
        }

//        String key = "746f74584172696e37395277457a71"; // 니 지하철 인증키
        String key = "436243644472696e36316879424961";

        StringBuilder urlBuilder = new StringBuilder("http://openapi.seoul.go.kr:8088");

        urlBuilder.append("/" + key);
        urlBuilder.append("/json");
        urlBuilder.append("/realtimeStationArrival");
        urlBuilder.append("/1/5");
        urlBuilder.append("/" + URLEncoder.encode(station, "UTF-8"));

        URL url = new URL(urlBuilder.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader rd;

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
            rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            rd = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }

        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = rd.readLine()) != null) {
            sb.append(line);
        }

        rd.close();
        conn.disconnect();

        return sb.toString();
    }
}