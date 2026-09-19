package com.example.demo.point.controller;

import com.example.demo.config.CurrentLanguage;
import com.example.demo.point.service.PointChargeService;
import com.example.demo.user.entity.User;
import com.example.demo.user.service.UserService;
import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PointPageController {

    private final UserService userService;
    private final PointChargeService pointChargeService;

    @GetMapping("/ko/mypage/points")
    public String points(Model model) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.findById(userId);

        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        model.addAttribute("lang", CurrentLanguage.get());
        model.addAttribute("user", user);
        model.addAttribute("chargeTiers", pointChargeService.chargeTiers());
        model.addAttribute("chargeHistory", pointChargeService.history(userId));
        model.addAttribute("portOneReady", pointChargeService.isReady());
        return "mypage/points";
    }
}
