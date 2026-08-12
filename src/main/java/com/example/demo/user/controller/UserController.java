package com.example.demo.user.controller;

import jakarta.servlet.http.HttpSession;
import com.example.demo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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

    @PostMapping("/signup")
    public String signup(@RequestParam(defaultValue = "personal") String accountType,
                         @RequestParam String loginId,
                         @RequestParam String password,
                         @RequestParam String passwordConfirm,
                         @RequestParam String name,
                         @RequestParam String email,
                         @RequestParam(required = false) String storeName,
                         @RequestParam(required = false) String businessRegistrationNumber,
                         @RequestParam(required = false) MultipartFile businessRegistrationFile,
                         @RequestParam(required = false) MultipartFile businessLicenseFile,
                         @RequestParam(required = false) MultipartFile menuFile,
                         @RequestParam(required = false) String lang,
                         HttpSession session,
                         Model model) {
        String selected = isSupportedLanguage(lang) ? lang : "ko";
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        model.addAttribute("signupMode", true);
        model.addAttribute("accountType", accountType);

        try {
            if (!password.equals(passwordConfirm)) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }
            userService.registerLocalUser(
                    accountType,
                    loginId,
                    password,
                    name,
                    email,
                    storeName,
                    businessRegistrationNumber,
                    originalFilename(businessRegistrationFile),
                    originalFilename(businessLicenseFile),
                    originalFilename(menuFile)
            );
            return "redirect:/login?registered=true&lang=" + selected;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("signupError", ex.getMessage());
            return "user/login";
        }
    }

    private String originalFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        return file.getOriginalFilename();
    }

    private boolean isSupportedLanguage(String language) {
        return "ko".equals(language) || "en".equals(language)
                || "ja".equals(language) || "zh-CN".equals(language);
    }
}
