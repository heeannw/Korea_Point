package com.example.demo.home.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.databind.type.LogicalType.Map;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("heroTitle", "Discover Korea");
        model.addAttribute("heroSubtitle", "Your gateway to the best attractions, food and travel tips.");

        model.addAttribute("slides", List.of(
                Map.of("imageUrl", "https://images.unsplash.com/photo-1538485399081-7191377e8241?w=1400&q=80"),
                Map.of("imageUrl", "https://images.unsplash.com/photo-1601621915196-2621bfb0cd6e?w=1400&q=80"),
                Map.of("imageUrl", "https://images.unsplash.com/photo-1549114473-8a17d3b72e3e?w=1400&q=80")
        ));

        model.addAttribute("categories", List.of(
                category("AT", "Attractions", "red", "/tourism"),
                category("FOOD", "Restaurants", "orange", "/tourism"),
                category("BUS", "Transport", "blue", "/transport"),
                category("PARK", "Nature", "green", "/tourism"),
                category("SHOP", "Shopping", "purple", "/tourism"),
                category("STAY", "Hotels", "teal", "/tourism"),
                category("TALK", "Community", "red", "/community")
        ));

        model.addAttribute("attractions", List.of(
                attraction("1", "Palace", "Gyeongbokgung Palace", "Jongno-gu, Seoul", "4.9", "2.3k", "https://images.unsplash.com/photo-1548115184-bc6544d06a58?w=600&q=80"),
                attraction("2", "District", "Bukchon Hanok Village", "Jongno-gu, Seoul", "4.8", "1.8k", "https://images.unsplash.com/photo-1538485399081-7191377e8241?w=600&q=80"),
                attraction("3", "Theme Park", "Everland Resort", "Yongin, Gyeonggi-do", "4.7", "3.1k", "https://images.unsplash.com/photo-1601621915196-2621bfb0cd6e?w=600&q=80")
        ));

        model.addAttribute("posts", List.of(
                post("Review", "cat-review", "Gyeongbokgung was absolutely stunning in hanbok!", "TravellerMike", "2 hours ago", "12"),
                post("Food", "cat-food", "Best Korean BBQ spots near Hongdae area", "FoodieJulia", "5 hours ago", "28"),
                post("Tip", "cat-tip", "How to use Seoul Metro as a foreigner", "SeoulExpert", "Yesterday", "45"),
                post("Review", "cat-review", "Cherry blossom season at Yeouido is worth the crowd!", "SakuraWatcher", "Yesterday", "9"),
                post("Food", "cat-food", "Must-try street food in Myeongdong 2025", "StreetFoodLover", "2 days ago", "33")
        ));

        return "home/home";
    }

    private Map<String, String> category(String icon, String label, String colorClass, String link) {
        return Map.of(
                "icon", icon,
                "label", label,
                "colorClass", colorClass,
                "link", link
        );
    }

    private Map<String, String> attraction(String rank, String badge, String title, String location, String rating, String reviews, String imageUrl) {
        return Map.of(
                "rank", rank,
                "badge", badge,
                "title", title,
                "location", location,
                "rating", rating,
                "reviews", reviews,
                "imageUrl", imageUrl
        );
    }

    private Map<String, String> post(String category, String categoryClass, String title, String author, String timeAgo, String comments) {
        return Map.of(
                "category", category,
                "categoryClass", categoryClass,
                "title", title,
                "author", author,
                "timeAgo", timeAgo,
                "comments", comments
        );
    }
}
