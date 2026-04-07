package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourismService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class TourismController {

    @Autowired
    TourismService tourismService;

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;


    @RequestMapping("/tour/all")
    public String tourAll(Model model)  {

        model.addAttribute("kakaoKey", kakaoKey);

        return "tourism/explore";
    }

    @RequestMapping("/tour/search/")
    public String tourSearch(@RequestParam(value = "keyword",required = false) String keyword,Model model,
                            HttpSession session
    )  {
        //키워드로 좌표찾기
        List<TourPlaceDto> list = null;
        list = tourismService.searchKeyword(keyword);

        if(list.isEmpty() || keyword.isEmpty() ){
            list = tourismService.analyzeLocation(37.5665,126.9780);
        }
        Double latsum = 0.0;
        Double lonsum = 0.0;

        for(TourPlaceDto p : list){
            latsum += p.getLat();
            lonsum += p.getLng();
        }

        Double centerLat = latsum/list.size();
        Double centerLon = lonsum/list.size();

        model.addAttribute("centerLat",centerLat);
        model.addAttribute("centerLon",centerLon);

        Double userlat = (Double)session.getAttribute("userlat");
        Double userlon =  (Double)session.getAttribute("userlon");

        model.addAttribute("userlat",userlat);
        model.addAttribute("userlon",userlon);

        model.addAttribute("list",list);
        model.addAttribute("kakaoKey",kakaoKey);
        return "tourism/explore";
    }

    @GetMapping("/tour/mylocation/{lat}/{lon}")
    public String MyLocation(@PathVariable Double lat, @PathVariable Double lon, Model model, HttpSession session){

        List<TourPlaceDto> nearbyList = tourismService.analyzeLocation(lat,lon);

        model.addAttribute("list", nearbyList);
        session.setAttribute("userlat",lat);
        session.setAttribute("userlon",lon);

        model.addAttribute("centerLat",lat);
        model.addAttribute("centerLon",lon);

        model.addAttribute("userlat",lat);
        model.addAttribute("userlon",lon);
        model.addAttribute("kakaoKey",kakaoKey);


        return "tourism/explore";
    }
}