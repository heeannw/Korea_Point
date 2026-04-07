package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourismService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class TourismController {

    @Autowired
    TourismService tourismService;

//    @GetMapping("/tour/test")
//    public List<Map<String, Object>> testRecommend() {
//
//        List<Map<String, Object>> candidates = new ArrayList<>();
//
//        // 관광지 1
//        Map<String, Object> place1 = new HashMap<>();
//        place1.put("title", "경복궁");
//        place1.put("type", "12");
//        place1.put("lat", 37.5796);
//        place1.put("lng", 126.9770);
//        place1.put("rating", 4.7);
//        place1.put("user_ratings_total", 52000);
//
//        List<Map<String, String>> reviews1 = new ArrayList<>();
//        Map<String, String> r1 = new HashMap<>();
//        r1.put("text", "Amazing place and beautiful palace");
//        r1.put("language", "en");
//        r1.put("rating", "5");
//        reviews1.add(r1);
//
//        Map<String, String> r2 = new HashMap<>();
//        r2.put("text", "정말 멋진 곳이에요");
//        r2.put("language", "ko");
//        r2.put("rating", "5");
//        reviews1.add(r2);
//
//        place1.put("reviews", reviews1);
//        candidates.add(place1);
//
//        // 관광지 2
//        Map<String, Object> place2 = new HashMap<>();
//        place2.put("title", "남산서울타워");
//        place2.put("type", "28");
//        place2.put("lat", 37.5512);
//        place2.put("lng", 126.9882);
//        place2.put("rating", 4.5);
//        place2.put("user_ratings_total", 43000);
//
//        List<Map<String, String>> reviews2 = new ArrayList<>();
//        Map<String, String> r3 = new HashMap<>();
//        r3.put("text", "Wonderful view and very impressive");
//        r3.put("language", "en");
//        r3.put("rating", "5");
//        reviews2.add(r3);
//
//        place2.put("reviews", reviews2);
//        candidates.add(place2);
//
//        // 시청 근처 좌표 예시
//        double userLat = 37.5665;
//        double userLng = 126.9780;
//
//        return tourismService.recommend(candidates, userLat, userLng, 5.0, 10);
//    }

    @RequestMapping("/tour/all")
    public String tourAll(Model model)  {

        model.addAttribute("kakaoKey","cd0022ebe44b8280a5811612a2069dd1");
        return "tourism/explore";
    }

    @RequestMapping("/tour/search")
    public String tourSearch(@RequestParam(value = "keyword",required = false) String keyword,Model model)  {
        List<TourPlaceDto> list = tourismService.searchKeyword("예술의");
//        tourismService.analyzeLocation()
        //System.out.println("tour / " + list.get(0).getTitle());
        //model.addAttribute("list",list);
        model.addAttribute("kakaoKey","cd0022ebe44b8280a5811612a2069dd1");
        return "tourism/explore";
    }

    @GetMapping("/tour/mylocation/{lat}/{lon}")
    public String MyLocation(@PathVariable Double lat, @PathVariable Double lon, Model model){

        List<TourPlaceDto> nearbyList = tourismService.analyzeLocation(lat,lon);

        model.addAttribute("list", nearbyList);
        model.addAttribute("userlat",lat);
        model.addAttribute("userlon",lon);
        model.addAttribute("kakaoKey","cd0022ebe44b8280a5811612a2069dd1");


        return "tourism/explore";
    }
}