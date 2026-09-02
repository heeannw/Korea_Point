package com.example.demo.home.controller;

import com.example.demo.home.service.HomeFestivalService;
import com.example.demo.home.service.HomeFoodService;
import com.example.demo.home.service.HomePlaceService;
import com.example.demo.home.service.HomeService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final HomeFestivalService homeFestivalService;
    private final HomePlaceService homePlaceService;
    private final HomeFoodService homeFoodService;


    // @GetMapping: 브라우저에서 GET 방식으로 "/" 또는 "/en" 에 접속했을 때 이 메서드 실행
    // Model model: 뷰(HTML)에 넘길 데이터를 담는 그릇
    @GetMapping({"/", "/en"})
    public String homeEn(Model model) {
        String lang = currentLang();
        // 언어 판단은 세션 로케일 하나로 통일한다. 공용 헤더가 ?lang= 으로 세션을 바꾸므로
        // 경로와 세션이 어긋나면 맞는 홈으로 보낸다.
        if (!"en".equals(lang)) {
            return "redirect:/ko";
        }
        session(model);
        model.addAttribute("lang", lang);
        populateModel(model, lang);
        return "home/home";         // templates/home/home.html 을 찾아서 렌더링
    }

    // @GetMapping: "/ko" 또는 "/kr" 접속 시 이 메서드 실행 (한국어 버전)
    @GetMapping({"/ko", "/kr"})
    public String homeKo(Model model) {
        String lang = currentLang();
        if ("en".equals(lang)) {
            return "redirect:/en";
        }
        session(model);
        model.addAttribute("lang", lang);
        // 일본어·중국어·불어는 국문 화면을 쓰되 장소 이름·주소는 해당 언어로 바꿔 끼운다
        // (TourPlaceI18n 오버레이가 en/ja/zh/fr 을 갖고 있다).
        populateModel(model, lang);
        return "home/home.ko";      // templates/home/home.ko.html 을 찾아서 렌더링
    }

    // homeEn, homeKo 두 메서드가 공통으로 사용하는 데이터 세팅 메서드
    // private: 이 클래스 내부에서만 사용하는 메서드
    // void: 반환값 없음 (Model 객체에 직접 데이터를 추가하는 방식이라 반환 불필요)
    /** 세션 로케일 → 우리가 다루는 언어 코드. 모르는 언어는 국문으로 본다. */
    private String currentLang() {
        String language = LocaleContextHolder.getLocale().getLanguage();
        return switch (language) {
            case "en", "ja", "zh", "fr" -> language;
            default -> "ko";
        };
    }

    private void session(Model model) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
    }

    private void populateModel(Model model, String lang) {


        // ── 히어로 섹션 텍스트 (영어) ──────────────────────────────────────────
        // Thymeleaf에서 ${heroTitle} 로 꺼내 쓸 수 있음
        model.addAttribute("heroTitle", "Discover Busan");

        // 히어로 서브타이틀 (영어)
        model.addAttribute("heroSubTitle", "Busan");

        // 히어로 설명 문구 (영어)
        model.addAttribute("heroDescription",
                "Festivals, beaches, and local food - everything you need for a trip to Busan.");

        // ── 히어로 섹션 텍스트 (한국어) ───────────────────────────────────────
        model.addAttribute("heroTitleKo", "부산을");
        model.addAttribute("heroSubTitleKo", "만나보세요");
        model.addAttribute("heroDescriptionKo",
                "지금 열리는 축제부터 바다와 맛집까지, 부산 여행에 필요한 정보를 한곳에 담았습니다.");

        // ── 히어로 슬라이더 이미지 ────────────────────────────────────────
        // 예전에는 서울 사진 3장(남산타워·북촌·광화문)이 위키미디어 주소로 박혀 있었다.
        // 부산 관광지 사진으로 바꾸되, 히어로는 배경이 잘리므로 저작권상
        // 변경이 허용된 사진만 쓴다(HomePlaceService 참고).
        model.addAttribute("slides", homePlaceService.getHeroImages(lang, 3));

        // ── 가볼 만한 곳 ─────────────────────────────────────────────────
        // 부산 관광지를 관광공사 API에서 받아온다. 예전에는 서울 3곳이 코드에
        // 박혀 있었고 별점·리뷰 수도 지어낸 값이었다. 둘 다 걷어냈다.
        model.addAttribute("places", homePlaceService.getRecommendedPlaces(lang, 6));

        // ── 부산 맛집 ────────────────────────────────────────────────────
        // 관광 탭이 음식점과 카페를 한 화면에서 탭으로 나누고 있어 홈도 한 섹션으로 묶는다.
        // 대신 카드가 한쪽으로 쏠리지 않게 음식점과 카페를 번갈아 담는다.
        model.addAttribute("foods", homeFoodService.getFoodPlaces(lang, 6));

        // ── 커뮤니티 게시글 리스트 ────────────────────────────────────────────
        // post() 헬퍼 메서드를 호출하여 각 게시글 Map 을 생성
        // 총 5개의 게시글 데이터를 리스트로 묶어서 "posts" 이름으로 Model에 추가
        // Thymeleaf에서 th:each 로 반복하며 각 post.title, post.author 등을 꺼내 씀
        model.addAttribute("trendingPosts", homeService.getTrendingPosts());

        // ── 지금 부산에서 열리는 축제 ────────────────────────────────────────
        // 관광공사 데이터 중 유일하게 기간이 있는 자료라, 접속 시점에 따라 화면이 바뀐다.
        // 대표이미지 보유율이 100%라 카드가 비지 않는다.
        model.addAttribute("festivals", homeFestivalService.getUpcomingFestivals(lang, 12));
    }

    // ── post 헬퍼 메서드 ────────────────────────────────────────────────────────
    // 게시글 데이터 하나를 Map<String, Object> 형태로 만들어 반환
    // attraction과 달리 Object 타입을 쓰는 이유: id가 Long 타입이라 String만으로는 담을 수 없음
    // 반환된 Map 하나 = 게시글 카드 하나
    private Map<String, Object> post(Long id, String category, String categoryKo, String categoryClass,
                                     String title, String titleKo,
                                     String author, String timeAgo, String timeAgoKo, String comments) {
        return Map.of(
                "id", id,                       // 게시글 고유 번호 (Long 타입, 나중에 상세 링크 등에 사용)
                "category", category,           // 영어 카테고리명 (Review, Food, Tip)
                "categoryKo", categoryKo,       // 한국어 카테고리명 (후기, 음식, 팁)
                "categoryClass", categoryClass, // CSS 클래스명 (cat-review, cat-food, cat-tip)
                "title", title,                 // 영어 게시글 제목
                "titleKo", titleKo,             // 한국어 게시글 제목
                "author", author,               // 작성자 닉네임
                "timeAgo", timeAgo,             // 영어 작성 시간 (2 hours ago, Yesterday 등)
                "timeAgoKo", timeAgoKo,         // 한국어 작성 시간 (2시간 전, 어제 등)
                "comments", comments            // 댓글 수 (문자열로 저장)
        );
    }
}
