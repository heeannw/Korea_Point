package com.example.demo.user.controller;

import jakarta.servlet.http.HttpSession;
import com.example.demo.user.entity.User;
import com.example.demo.user.service.OwnerRequestService;
import com.example.demo.user.service.UserService;
import com.example.demo.user.util.SecurityUtil;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OwnerRequestService ownerRequestService;

    // Spring Security OAuth2의 loginPage("/login")이 이 URL을 바라봄
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String lang,
                            @RequestParam(required = false, defaultValue = "false") boolean signup,
                            @RequestParam(required = false) String redirect,
                            HttpSession session, Model model) {
        String selected = lang;
        if (!isSupportedLanguage(selected)) {
            Object saved = session.getAttribute("loginLanguage");
            selected = saved instanceof String value && isSupportedLanguage(value)
                    ? value : "ko";
        }
        if (isSafeRedirectPath(redirect)) {
            session.setAttribute("loginRedirectUrl", redirect);
        }
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        model.addAttribute("signupMode", signup);
        return "user/login";
    }

    @GetMapping("/login/success")
    public String loginSuccess(@RequestParam(required = false) String lang,
                               HttpSession session,
                               Model model) {
        String selected = isSupportedLanguage(lang) ? lang : "ko";
        session.setAttribute("loginLanguage", selected);
        Object redirect = session.getAttribute("loginRedirectUrl");
        session.removeAttribute("loginRedirectUrl");
        String targetUrl = redirect instanceof String value && isSafeRedirectPath(value)
                ? value
                : ("ko".equals(selected) ? "/ko" : "/");
        model.addAttribute("targetUrl", targetUrl);
        return "user/login-success";
    }

    @GetMapping("/ko/settings")
    public String settings(Model model) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.findById(userId);
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", user.getName());
        model.addAttribute("user", user);
        model.addAttribute("hasProvidedEmail", userService.hasProvidedEmail(user));
        model.addAttribute("isLocalAccount", "local".equals(user.getProvider()));
        return "user/settings";
    }

    @PostMapping("/ko/settings/profile")
    public String updateProfile(@RequestParam String name,
                                RedirectAttributes redirectAttributes) {
        try {
            userService.updateProfile(SecurityUtil.getCurrentUserId(), name);
            redirectAttributes.addFlashAttribute("settingsMessage", "프로필 정보가 저장됐어요.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("settingsError", ex.getMessage());
        }
        return "redirect:/ko/settings";
    }

    @PostMapping("/ko/settings/password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String passwordConfirm,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.changeLocalPassword(
                    SecurityUtil.getCurrentUserId(),
                    currentPassword,
                    newPassword,
                    passwordConfirm
            );
            redirectAttributes.addFlashAttribute("settingsMessage", "비밀번호가 변경됐어요.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("settingsError", ex.getMessage());
        }
        return "redirect:/ko/settings";
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

    @PostMapping("/find-login-id")
    public String findLoginId(@RequestParam String name,
                              @RequestParam String email,
                              @RequestParam(required = false) String lang,
                              HttpSession session,
                              Model model) {
        String selected = isSupportedLanguage(lang) ? lang : "ko";
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        model.addAttribute("activePanel", "findIdPanel");
        try {
            String loginId = userService.findLocalLoginId(name, email)
                    .orElseThrow(() -> new IllegalArgumentException("일치하는 아이디를 찾을 수 없습니다."));
            model.addAttribute("findIdResult", "회원님의 아이디는 " + loginId + " 입니다.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("findIdError", ex.getMessage());
        }
        return "user/login";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String loginId,
                                @RequestParam String email,
                                @RequestParam String newPassword,
                                @RequestParam String passwordConfirm,
                                @RequestParam(required = false) String lang,
                                HttpSession session,
                                Model model) {
        String selected = isSupportedLanguage(lang) ? lang : "ko";
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        model.addAttribute("activePanel", "resetPasswordPanel");
        try {
            userService.resetLocalPassword(loginId, email, newPassword, passwordConfirm);
            model.addAttribute("resetPasswordResult", "비밀번호가 변경됐어요. 새 비밀번호로 로그인해 주세요.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("resetPasswordError", ex.getMessage());
        }
        return "user/login";
    }

    private boolean isSupportedLanguage(String language) {
        return "ko".equals(language) || "en".equals(language)
                || "ja".equals(language) || "zh-CN".equals(language);
    }

    private boolean isSafeRedirectPath(String redirect) {
        return redirect != null
                && redirect.startsWith("/")
                && !redirect.startsWith("//")
                && !redirect.startsWith("/login");
    }
}
