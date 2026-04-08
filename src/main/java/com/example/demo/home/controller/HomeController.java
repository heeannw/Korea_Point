package com.example.demo.home.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    @GetMapping({"/", "/en"})
    public String homeEn(Model model) {
        populateModel(model);
        return "home/home";
    }

    @GetMapping({"/ko", "/kr"})
    public String homeKo(Model model) {
        populateModel(model);
        return "home/home-ko";
    }

    private void populateModel(Model model) {
        // Updated English hero copy for clearer first-read readability.
        model.addAttribute("heroTitle", "Discover Korea");
        model.addAttribute("heroSubTitle", "Korea");
        model.addAttribute("heroDescription",
                "Curated guides to the best attractions, food, and local experiences.");

        model.addAttribute("heroTitleKo", "한국을");
        model.addAttribute("heroSubTitleKo", "만나보세요");
        model.addAttribute("heroDescriptionKo",
                "대표 관광지부터 맛집, 현지 정보까지 한국 여행에 필요한 정보를 한곳에 담았습니다.");

        model.addAttribute("slides", List.of(
                Map.of("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/c/c3/KOCIS_Korea_BlueSky_In_Seoul_03_%289947223046%29.jpg"),
                Map.of("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/a/ab/Bukchon-ro_11-gil_street_with_hanok_houses_and_blue_sky_in_Bukchon_Hanok_Village_Seoul.jpg"),
                Map.of("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/9/96/Gwanghwamun_at_Night.jpg")
        ));

        model.addAttribute("attractions", List.of(
                attraction("1", "Gyeongbokgung Palace", "경복궁", "Jongno-gu, Seoul", "서울 종로구", "4.9", "2.3k",
                        "https://upload.wikimedia.org/wikipedia/commons/6/67/Gyeongbokgung_Palace%2C_Seoul%2C_South_Korea_%2814712071910%29.jpg"),
                attraction("2", "Bukchon Hanok Village", "북촌한옥마을", "Jongno-gu, Seoul", "서울 종로구", "4.8", "1.8k",
                        "https://upload.wikimedia.org/wikipedia/commons/d/da/Bukchon_Hanok_Village%2C_Seoul_%289%29_%2839304631270%29.jpg"),
                attraction("3", "Namsan Seoul Tower", "남산서울타워", "Yongsan-gu, Seoul", "서울 용산구", "4.7", "3.1k",
                        "https://upload.wikimedia.org/wikipedia/commons/b/b4/N_Seoul_Tower_%286%29.jpg")
        ));

        model.addAttribute("posts", List.of(
                post(1L, "Review", "후기", "cat-review",
                        "Gyeongbokgung was absolutely stunning in hanbok!",
                        "한복 입고 본 경복궁이 정말 아름다웠어요!",
                        "TravellerMike", "2 hours ago", "2시간 전", "12"),
                post(2L, "Food", "음식", "cat-food",
                        "Best Korean BBQ spots near Hongdae area",
                        "홍대 근처에서 가기 좋은 한식 바비큐 맛집",
                        "FoodieJulia", "5 hours ago", "5시간 전", "28"),
                post(3L, "Tip", "팁", "cat-tip",
                        "How to use Seoul Metro as a foreigner",
                        "외국인도 쉽게 쓰는 서울 지하철 이용법",
                        "SeoulExpert", "Yesterday", "어제", "45"),
                post(4L, "Review", "후기", "cat-review",
                        "Cherry blossom season at Yeouido is worth the crowd!",
                        "여의도 벚꽃 시즌, 사람 많아도 갈 가치가 있어요!",
                        "SakuraWatcher", "Yesterday", "어제", "9"),
                post(5L, "Food", "음식", "cat-food",
                        "Must-try street food in Myeongdong 2025",
                        "2025 명동에서 꼭 먹어봐야 할 길거리 음식",
                        "StreetFoodLover", "2 days ago", "2일 전", "33")
        ));
    }

    private Map<String, String> attraction(String rank, String title, String titleKo,
                                           String location, String locationKo,
                                           String rating, String reviews, String imageUrl) {
        return Map.of(
                "rank", rank,
                "title", title,
                "titleKo", titleKo,
                "location", location,
                "locationKo", locationKo,
                "rating", rating,
                "reviews", reviews,
                "imageUrl", imageUrl
        );
    }

    private Map<String, Object> post(Long id, String category, String categoryKo, String categoryClass,
                                     String title, String titleKo,
                                     String author, String timeAgo, String timeAgoKo, String comments) {
        return Map.of(
                "id", id,
                "category", category,
                "categoryKo", categoryKo,
                "categoryClass", categoryClass,
                "title", title,
                "titleKo", titleKo,
                "author", author,
                "timeAgo", timeAgo,
                "timeAgoKo", timeAgoKo,
                "comments", comments
        );
    }
}
