package com.example.demo.user.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    // Spring Security OAuth2의 loginPage("/login")이 이 URL을 바라봄
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String lang,
                            HttpSession session, Model model) {
        String selected = lang;
        if (!isSupportedLanguage(selected)) {
            Object saved = session.getAttribute("loginLanguage");
            selected = saved instanceof String value && isSupportedLanguage(value)
                    ? value : "ko";
        }
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        return "user/login";
    }

    private boolean isSupportedLanguage(String language) {
        return "ko".equals(language) || "en".equals(language)
                || "ja".equals(language) || "zh-CN".equals(language);
    }
}
