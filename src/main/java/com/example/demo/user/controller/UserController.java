package com.example.demo.user.controller;

import jakarta.servlet.http.HttpSession;
import com.example.demo.user.entity.User;
import com.example.demo.user.service.OwnerRequestService;
import com.example.demo.user.service.UserService;
import org.springframework.transaction.annotation.Transactional;
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
    private final OwnerRequestService ownerRequestService;

    // Spring Security OAuth2의 loginPage("/login")이 이 URL을 바라봄
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String lang,
                            @RequestParam(required = false, defaultValue = "false") boolean signup,
                            HttpSession session, Model model) {
        String selected = lang;
        if (!isSupportedLanguage(selected)) {
            Object saved = session.getAttribute("loginLanguage");
            selected = saved instanceof String value && isSupportedLanguage(value)
                    ? value : "ko";
        }
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        model.addAttribute("signupMode", signup);
        return "user/login";
    }

    @PostMapping("/signup")
    @Transactional
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
            User user = userService.registerLocalUser(
                    loginId,
                    password,
                    name,
                    email
            );
            if ("owner".equals(accountType)) {
                ownerRequestService.apply(
                        user.getId(),
                        storeName,
                        businessRegistrationNumber,
                        businessRegistrationFile,
                        businessLicenseFile,
                        menuFile
                );
            }
            return "redirect:/login?registered=true&lang=" + selected;
        } catch (IllegalArgumentException ex) {
            model.addAttribute("signupError", ex.getMessage());
            return "user/login";
        } catch (IllegalStateException ex) {
            model.addAttribute("signupError", ex.getMessage());
            return "user/login";
        }
    }

    private boolean isSupportedLanguage(String language) {
        return "ko".equals(language) || "en".equals(language)
                || "ja".equals(language) || "zh-CN".equals(language);
    }
}
