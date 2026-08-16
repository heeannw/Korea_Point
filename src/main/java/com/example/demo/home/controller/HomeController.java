package com.example.demo.home.controller;

import com.example.demo.home.service.HomeService;
import com.example.demo.reservation.service.TourPhotoGalleryService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final TourPhotoGalleryService tourPhotoGalleryService;


    // @GetMapping: 브라우저에서 GET 방식으로 "/" 또는 "/en" 에 접속했을 때 이 메서드 실행
    // Model model: 뷰(HTML)에 넘길 데이터를 담는 그릇
    @GetMapping({"/", "/en"})
    public String homeEn(Model model) {
        session(model);
        populateModel(model);       // 공통 데이터 세팅 메서드 호출
        return "home/home";         // templates/home/home.html 을 찾아서 렌더링
    }

    // @GetMapping: "/ko" 또는 "/kr" 접속 시 이 메서드 실행 (한국어 버전)
    @GetMapping({"/ko", "/kr"})
    public String homeKo(Model model) {
        session(model);
        populateModel(model);       // 공통 데이터 세팅 메서드 호출 (영어와 동일한 데이터)
        return "home/home.ko";      // templates/home/home.ko.html 을 찾아서 렌더링
    }

    // homeEn, homeKo 두 메서드가 공통으로 사용하는 데이터 세팅 메서드
    // private: 이 클래스 내부에서만 사용하는 메서드
    // void: 반환값 없음 (Model 객체에 직접 데이터를 추가하는 방식이라 반환 불필요)
    private void session(Model model) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
    }

    private void populateModel(Model model) {


        // ── 히어로 섹션 텍스트 (영어) ──────────────────────────────────────────
        // Thymeleaf에서 ${heroTitle} 로 꺼내 쓸 수 있음
        model.addAttribute("heroTitle", "Discover Korea");

        // 히어로 서브타이틀 (영어)
        model.addAttribute("heroSubTitle", "Korea");

        // 히어로 설명 문구 (영어)
        model.addAttribute("heroDescription",
                "Curated guides to the best attractions, food, and local experiences.");

        // ── 히어로 섹션 텍스트 (한국어) ───────────────────────────────────────
        model.addAttribute("heroTitleKo", "부산을");
        model.addAttribute("heroSubTitleKo", "만나보세요");
        model.addAttribute("heroDescriptionKo",
                "대표 관광지부터 맛집, 현지 정보까지 부산 여행에 필요한 정보를 한곳에 담았습니다.");

        // ── 히어로 슬라이더 이미지 리스트 ────────────────────────────────────
        // List.of(): 수정 불가능한 리스트 생성 (3개 슬라이드)
        // 각 슬라이드는 "imageUrl" 키 하나만 가진 Map으로 구성
        // Thymeleaf에서 th:each 로 반복하며 각 slide.imageUrl 을 꺼내 씀
        model.addAttribute("slides", getBusanPhotoSlides());

        // ── 추천 관광지 카드 리스트 ────────────────────────────────────────────
        // attraction() 헬퍼 메서드를 호출하여 각 관광지 Map 을 생성
        // 총 3개의 관광지 데이터를 리스트로 묶어서 "attractions" 이름으로 Model에 추가
        // Thymeleaf에서 th:each 로 반복하며 각 attraction.title, attraction.rating 등을 꺼내 씀
        model.addAttribute("attractions", List.of(
                attraction("1", "Gyeongbokgung Palace", "경복궁", "Jongno-gu, Seoul", "서울 종로구", "4.9", "2.3k",
                        "https://upload.wikimedia.org/wikipedia/commons/4/46/%EA%B2%BD%EB%B3%B5%EA%B6%81_%ED%9D%A5%EB%A1%80%EB%AC%B8_%EC%A0%84%EC%B2%B4_%EC%A0%84%EA%B2%BD%28%EA%B6%81%EB%8A%A5%EC%9C%A0%EC%A0%81%EB%B3%B8%EB%B6%80%29-min.jpg"),
                attraction("2", "Bukchon Hanok Village", "북촌한옥마을", "Jongno-gu, Seoul", "서울 종로구", "4.8", "1.8k",
                        "https://upload.wikimedia.org/wikipedia/commons/a/a5/Bukchon-ro_11-gil_street_with_hanok_houses_and_wooden_gate_in_Bukchon_Hanok_Village_Seoul.jpg"),
                attraction("3", "Namsan Seoul Tower", "남산서울타워", "Yongsan-gu, Seoul", "서울 용산구", "4.7", "3.1k",
                        "https://images.pexels.com/photos/2848492/pexels-photo-2848492.jpeg?cs=srgb&dl=pexels-pixabay-2848492.jpg&fm=jpg")
        ));

        // ── 커뮤니티 게시글 리스트 ────────────────────────────────────────────
        // post() 헬퍼 메서드를 호출하여 각 게시글 Map 을 생성
        // 총 5개의 게시글 데이터를 리스트로 묶어서 "posts" 이름으로 Model에 추가
        // Thymeleaf에서 th:each 로 반복하며 각 post.title, post.author 등을 꺼내 씀
        model.addAttribute("trendingPosts", homeService.getTrendingPosts());
    }

    private List<Map<String, String>> getBusanPhotoSlides() {
        List<String> imageUrls = new ArrayList<>(tourPhotoGalleryService.findBusanHeroImageUrls(3));
        if (imageUrls.size() < 3) {
            fallbackBusanSlides().stream()
                    .map(slide -> slide.get("imageUrl"))
                    .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                    .filter(imageUrl -> !imageUrls.contains(imageUrl))
                    .limit(3 - imageUrls.size())
                    .forEach(imageUrls::add);
        }

        List<Map<String, String>> apiSlides = imageUrls.stream()
                .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                .distinct()
                .limit(3)
                .map(imageUrl -> Map.of("imageUrl", imageUrl))
                .collect(Collectors.toList());

        return apiSlides.isEmpty() ? fallbackBusanSlides() : apiSlides;
    }

    private List<Map<String, String>> fallbackBusanSlides() {
        return List.of(
                Map.of("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/9/9f/Haeundae_Beach_in_Busan.jpg"),
                Map.of("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/8/88/Gamcheon_Culture_Village%2C_Busan%2C_Korea.jpg"),
                Map.of("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/4/46/Busan_Gwangandaegyo_Bridge.jpg")
        );
    }

    // ── attraction 헬퍼 메서드 ──────────────────────────────────────────────────
    // 관광지 데이터 하나를 Map<String, String> 형태로 만들어 반환
    // Map.of()의 최대 인자 쌍은 10개 → 딱 8쌍 사용 중 (rank, title, titleKo, location, locationKo, rating, reviews, imageUrl)
    // 반환된 Map 하나 = 관광지 카드 하나
    private Map<String, String> attraction(String rank, String title, String titleKo,
                                           String location, String locationKo,
                                           String rating, String reviews, String imageUrl) {
        return Map.of(
                "rank", rank,           // 순위 번호 (1, 2, 3)
                "title", title,         // 영어 관광지 이름
                "titleKo", titleKo,     // 한국어 관광지 이름
                "location", location,   // 영어 위치
                "locationKo", locationKo, // 한국어 위치
                "rating", rating,       // 별점 (예: "4.9")
                "reviews", reviews,     // 리뷰 수 (예: "2.3k")
                "imageUrl", imageUrl    // 관광지 이미지 URL
        );
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
