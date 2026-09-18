package com.example.demo.admin.controller;

import com.example.demo.user.util.SecurityUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminPageController {
    @org.springframework.beans.factory.annotation.Value("${TOURISM_KAKAO_API_KEY:}")
    private String kakaoKey;

    @GetMapping
    public String dashboard(Model model) {
        return adminPage(model, "dashboard", "관리자 대시보드");
    }

    @GetMapping("/users")
    public String users(Model model) {
        return adminPage(model, "users", "사용자 관리");
    }

    @GetMapping("/reservation-places")
    public String reservationPlaces(Model model) {
        return adminPage(model, "reservationPlaces", "장소 예약 설정");
    }

    @GetMapping("/reservation-requests")
    public String reservationRequests(Model model) {
        return adminPage(model, "reservationRequests", "예약 신청 검토");
    }

    @GetMapping("/owner-applications")
    public String ownerApplications(Model model) {
        return adminPage(model, "ownerApplications", "사업자 신청 관리");
    }

    @GetMapping("/posts")
    public String posts(Model model) {
        return adminPage(model, "posts", "게시글 관리");
    }

    @GetMapping("/comments")
    public String comments(Model model) {
        return adminPage(model, "comments", "댓글 관리");
    }

    @GetMapping("/categories")
    public String categories() {
        return "redirect:/admin";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        return adminPage(model, "reports", "신고 관리");
    }

    @GetMapping("/logs")
    public String logs(Model model) {
        return adminPage(model, "logs", "작업 로그");
    }

    private String adminPage(Model model, String activePage, String pageTitle) {
        model.addAttribute("activePage", activePage);
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("kakaoKey", kakaoKey);
        // 사용자 목록에서 자기 자신에게 정지 버튼을 내지 않으려고 쓴다.
        model.addAttribute("currentUserId", SecurityUtil.getCurrentUserId());
        return "admin/index";
    }
}
