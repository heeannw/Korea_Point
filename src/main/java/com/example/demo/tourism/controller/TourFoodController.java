package com.example.demo.tourism.controller;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourismService;
import com.example.demo.user.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Set;

/**
 * 음식점과 카페를 한 화면에서 보여준다.
 * 기존 foodmap/cafe 화면은 서울 기준 로컬 데이터를 쓰지만 여기서는 관광 목록과 같은 TourAPI를 쓴다.
 */
@Controller
public class TourFoodController {

    private static final double BUSAN_CENTER_LAT = 35.1796;
    private static final double BUSAN_CENTER_LON = 129.0756;

    private final TourismService tourismService;

    @Value("${TOURISM_KAKAO_API_KEY}")
    private String kakaoKey;

    public TourFoodController(TourismService tourismService) {
        this.tourismService = tourismService;
    }

    /**
     * lat/lon은 "이 지점 기준으로 보여달라"는 뜻이라 세션에 저장하지 않는다.
     * 관광지 상세에서 넘어올 때 그 관광지 좌표가 들어오는데,
     * 세션에 쓰면 사용자의 실제 위치를 덮어써 관광 화면의 거리까지 틀어진다.
     */
    @GetMapping("/tour/food")
    public String foodList(@RequestParam(value = "lang", defaultValue = "ko") String lang,
                           @RequestParam(value = "lat", required = false) Double lat,
                           @RequestParam(value = "lon", required = false) Double lon,
                           @RequestParam(value = "category", required = false) String category,
                           @RequestParam(value = "near", required = false) String near,
                           Model model, HttpSession session) {
        return listPage(lang, lat, lon, category, near, model, session, false);
    }

    /** 카페·찻집은 성격이 달라 목록을 따로 뗀다. 화면은 음식점과 같은 템플릿을 쓴다. */
    @GetMapping("/tour/cafe")
    public String cafeList(@RequestParam(value = "lang", defaultValue = "ko") String lang,
                           @RequestParam(value = "lat", required = false) Double lat,
                           @RequestParam(value = "lon", required = false) Double lon,
                           @RequestParam(value = "category", required = false) String category,
                           @RequestParam(value = "near", required = false) String near,
                           Model model, HttpSession session) {
        return listPage(lang, lat, lon, category, near, model, session, true);
    }

    private String listPage(String lang, Double lat, Double lon, String category, String near,
                            Model model, HttpSession session, boolean cafeOnly) {
        Double originLat = lat != null ? lat : sessionLat(session);
        Double originLon = lon != null ? lon : sessionLon(session);

        List<TourPlaceDto> places = cafeOnly
                ? tourismService.cafeList(normalize(lang))
                : tourismService.foodList(normalize(lang));
        if (originLat != null && originLon != null) {
            tourismService.allDistence(originLat, originLon, places);
        }
        model.addAttribute("category", category);
        model.addAttribute("near", near);
        model.addAttribute("cafeOnly", cafeOnly);
        return renderPage(model, places, normalize(lang), originLat, originLon, null);
    }

    @GetMapping("/tour/food/search")
    public String foodSearch(@RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "lang", defaultValue = "ko") String lang,
                             @RequestParam(value = "cafeOnly", defaultValue = "false") boolean cafeOnly,
                             Model model, HttpSession session) {
        Double userLat = sessionLat(session);
        Double userLon = sessionLon(session);
        List<TourPlaceDto> places =
                tourismService.foodSearch(keyword, userLat, userLon, normalize(lang), cafeOnly);
        model.addAttribute("category", null);
        model.addAttribute("near", null);
        model.addAttribute("cafeOnly", cafeOnly);
        return renderPage(model, places, normalize(lang), userLat, userLon, keyword);
    }

    /**
     * 미래 인기 공급자 연결을 위한 공통 TOP 20 API다.
     * 공급자가 비활성인 현재는 빈 배열을 반환하므로 화면에 가짜 순위가 나타나지 않는다.
     */
    @GetMapping("/tour/api/popular/food")
    @ResponseBody
    public List<TourPlaceDto> popularFood(
            @RequestParam(value = "section", defaultValue = "food") String section,
            @RequestParam(value = "lang", defaultValue = "ko") String lang,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        boolean cafeOnly = "cafe".equalsIgnoreCase(section);
        return tourismService.popularFoodList(normalize(lang), cafeOnly, limit);
    }

    private String renderPage(Model model, List<TourPlaceDto> places, String lang,
                              Double userLat, Double userLon, String keyword) {
        model.addAttribute("list", places);
        model.addAttribute("lang", lang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("kakaoKey", kakaoKey);
        model.addAttribute("userlat", userLat);
        model.addAttribute("userlon", userLon);
        // 지도 중심은 항상 부산이다. 현위치가 서울이면 빈 지도가 뜨기 때문이다.
        model.addAttribute("centerLat", BUSAN_CENTER_LAT);
        model.addAttribute("centerLon", BUSAN_CENTER_LON);
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        return "tourism/food";
    }

    /** 지원 언어. 그 밖의 값은 국문으로 되돌린다. TourApiProperties.baseUrlFor와 맞춰 둔다. */
    private static final Set<String> SUPPORTED_LANGS = Set.of("ko", "en", "ja", "zh", "fr");

    private String normalize(String lang) {
        if (lang == null) return "ko";
        String lower = lang.toLowerCase();
        return SUPPORTED_LANGS.contains(lower) ? lower : "ko";
    }

    /** 관광 화면이 저장해 둔 사용자 위치를 읽기만 한다. 이 화면에서는 쓰지 않는다. */
    private Double sessionLat(HttpSession session) {
        return (Double) session.getAttribute("userlat");
    }

    private Double sessionLon(HttpSession session) {
        return (Double) session.getAttribute("userlon");
    }
}
