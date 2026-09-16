package com.example.demo.user.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.demo.config.CurrentLanguage;
import com.example.demo.user.entity.User;
import com.example.demo.user.service.EmailVerificationService;
import com.example.demo.user.service.OwnerRequestService;
import com.example.demo.user.service.UserService;
import com.example.demo.user.util.SecurityUtil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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
    private final EmailVerificationService emailVerificationService;

    // Spring Security OAuth2의 loginPage("/login")이 이 URL을 바라봄
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String lang,
                            @RequestParam(required = false, defaultValue = "false") boolean signup,
                            @RequestParam(required = false) String redirect,
                            HttpSession session, Model model) {
        String selected = selectedLanguage(lang);
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
                               @RequestParam(required = false) String provider,
                               HttpSession session,
                               Model model) {
        String selected = selectedLanguage(lang);
        session.setAttribute("loginLanguage", selected);
        Object redirect = session.getAttribute("loginRedirectUrl");
        session.removeAttribute("loginRedirectUrl");
        String targetUrl = redirect instanceof String value && isSafeRedirectPath(value)
                ? value
                : ("ko".equals(selected) ? "/ko" : "/");
        model.addAttribute("targetUrl", targetUrl);
        model.addAttribute("lang", selected);
        model.addAttribute("provider", provider);
        model.addAttribute("showNationalityPrompt",
                SecurityUtil.isLoggedIn()
                        && userService.shouldShowNationalityPrompt(SecurityUtil.getCurrentUserId()));
        return "user/login-success";
    }

    @GetMapping("/ko/settings")
    public String settings(HttpSession session, Model model) {
        Long userId = SecurityUtil.getCurrentUserId();
        User user = userService.findById(userId);
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("currentUserName", user.getName());
        model.addAttribute("user", user);
        model.addAttribute("hasProvidedEmail", userService.hasProvidedEmail(user));
        model.addAttribute("isLocalAccount", "local".equals(user.getProvider()));
        model.addAttribute("loginProviderName", userService.loginProviderName(user));
        model.addAttribute("pendingEmail", emailVerificationService.getPendingEmail(session));
        return "user/settings";
    }

    @PostMapping("/ko/settings/nationality")
    public String updateNationality(@RequestParam(required = false) String nationality,
                                    @RequestParam(required = false) String skip,
                                    @RequestParam(required = false) String redirect,
                                    RedirectAttributes redirectAttributes) {
        try {
            if ("true".equals(skip)) {
                userService.skipNationalityPrompt(SecurityUtil.getCurrentUserId());
            } else {
                userService.updateNationality(SecurityUtil.getCurrentUserId(), nationality);
            }
            if (!isSafeRedirectPath(redirect)) {
                redirectAttributes.addFlashAttribute("settingsMessage", "국적 설정이 저장됐어요.");
            }
        } catch (IllegalArgumentException ex) {
            if (!isSafeRedirectPath(redirect)) {
                redirectAttributes.addFlashAttribute("settingsError", ex.getMessage());
            }
        }
        if (isSafeRedirectPath(redirect)) {
            return "redirect:" + redirect;
        }
        return "redirect:/ko/settings#nationality";
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
            redirectAttributes.addFlashAttribute("passwordMessage", "비밀번호가 변경됐어요.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("passwordError", ex.getMessage());
        }
        return "redirect:/ko/settings#password";
    }

    @PostMapping("/ko/settings/email-code")
    public String sendEmailCode(@RequestParam String newEmail,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findById(SecurityUtil.getCurrentUserId());
            if (!"local".equals(user.getProvider())) {
                throw new IllegalArgumentException("소셜 로그인 계정은 이메일을 직접 변경할 수 없습니다.");
            }
            emailVerificationService.sendEmailChangeCode(session, newEmail);
            redirectAttributes.addFlashAttribute("emailMessage", "인증번호를 메일로 보냈어요. 5분 안에 입력해 주세요. 메일이 안 보이면 스팸함도 확인해 주세요.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("emailError", ex.getMessage());
        }
        return "redirect:/ko/settings#email";
    }

    @PostMapping("/ko/settings/email")
    public String updateEmail(@RequestParam String newEmail,
                              @RequestParam String verificationCode,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        try {
            String verifiedEmail = emailVerificationService.verifyEmailChangeCode(session, newEmail, verificationCode);
            userService.updateLocalEmail(SecurityUtil.getCurrentUserId(), verifiedEmail);
            redirectAttributes.addFlashAttribute("emailMessage", "이메일이 변경됐어요.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("emailError", ex.getMessage());
        }
        return "redirect:/ko/settings#email";
    }

    @PostMapping("/ko/settings/withdraw")
    public String withdrawAccount(@RequestParam(required = false) String currentPassword,
                                  @RequestParam(required = false) String confirmationText,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  RedirectAttributes redirectAttributes) {
        try {
            userService.withdrawAccount(SecurityUtil.getCurrentUserId(), currentPassword, confirmationText);
            redirectAttributes.addFlashAttribute("withdrawMessage", "계정 탈퇴가 완료됐어요. 언제든 다시 가입할 수 있어요.");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // 로그아웃하면 세션이 비워져 언어도 함께 사라진다. 비우기 전에 읽어 두었다가 주소로 넘긴다.
            String lang = CurrentLanguage.get();
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/login?lang=" + lang;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("withdrawError", ex.getMessage());
            return "redirect:/ko/settings#danger";
        }
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
                         @RequestParam(required = false) String ownerName,
                         @RequestParam(required = false) String startDate,
                         @RequestParam(required = false) String businessRegistrationNumber,
                         @RequestParam(required = false) MultipartFile businessRegistrationFile,
                         @RequestParam(required = false) MultipartFile businessLicenseFile,
                         @RequestParam(required = false) MultipartFile menuFile,
                         @RequestParam(required = false) String lang,
                         HttpSession session,
                         Model model) {
        String selected = selectedLanguage(lang);
        session.setAttribute("loginLanguage", selected);
        model.addAttribute("lang", selected);
        model.addAttribute("signupMode", true);
        model.addAttribute("accountType", accountType);

        try {
            if (!password.equals(passwordConfirm)) {
                throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
            }
            OwnerRequestService.OwnerApplicationDraft ownerDraft = null;
            if ("owner".equals(accountType)) {
                ownerDraft = ownerRequestService.prepareApplication(
                        storeName,
                        ownerName,
                        startDate,
                        businessRegistrationNumber,
                        businessRegistrationFile,
                        businessLicenseFile,
                        menuFile
                );
            }
            User user = userService.registerLocalUser(
                    loginId,
                    password,
                    name,
                    email
            );
            if (ownerDraft != null) {
                ownerRequestService.apply(user.getId(), ownerDraft);
            }
            return "redirect:/login?registered=true&lang=" + selected;
        } catch (IllegalArgumentException ex) {
            restoreSignupForm(model, loginId, name, email, storeName, ownerName, startDate, businessRegistrationNumber);
            model.addAttribute("signupError", signupErrorMessage(accountType, ex.getMessage()));
            return "user/login";
        } catch (IllegalStateException ex) {
            restoreSignupForm(model, loginId, name, email, storeName, ownerName, startDate, businessRegistrationNumber);
            model.addAttribute("signupError", signupErrorMessage(accountType, ex.getMessage()));
            return "user/login";
        }
    }

    @PostMapping("/find-login-id")
    public String findLoginId(@RequestParam String name,
                              @RequestParam String email,
                              @RequestParam(required = false) String lang,
                              HttpSession session,
                              Model model) {
        String selected = selectedLanguage(lang);
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
        String selected = selectedLanguage(lang);
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

    /**
     * 로그인 화면의 언어. 주소로 온 값이 있으면 그것을, 없으면 지금 사이트 언어를 쓴다.
     * 예전에는 없으면 한국어로 떨어져, 일본어로 보던 사람도 로그인 화면은 한국어로 봤다.
     */
    private String selectedLanguage(String lang) {
        return isSupportedLanguage(lang) ? normalizeLanguage(lang) : CurrentLanguage.get();
    }

    private boolean isSupportedLanguage(String language) {
        if (language == null || language.isBlank()) {
            return false;
        }
        String normalized = normalizeLanguage(language);
        return "ko".equals(normalized) || "en".equals(normalized)
                || "ja".equals(normalized) || "zh".equals(normalized)
                || "fr".equals(normalized);
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "ko";
        }
        String normalized = language.trim().toLowerCase().replace('_', '-');
        if (normalized.startsWith("en")) return "en";
        if (normalized.startsWith("ja") || normalized.startsWith("jp")) return "ja";
        if (normalized.startsWith("zh")) return "zh";
        if (normalized.startsWith("fr")) return "fr";
        return "ko";
    }

    private boolean isSafeRedirectPath(String redirect) {
        return redirect != null
                && redirect.startsWith("/")
                && !redirect.startsWith("//")
                && !redirect.startsWith("/login");
    }

    private void restoreSignupForm(Model model,
                                   String loginId,
                                   String name,
                                   String email,
                                   String storeName,
                                   String ownerName,
                                   String startDate,
                                   String businessRegistrationNumber) {
        model.addAttribute("signupLoginId", loginId);
        model.addAttribute("signupName", name);
        model.addAttribute("signupEmail", email);
        model.addAttribute("storeName", storeName);
        model.addAttribute("ownerName", ownerName);
        model.addAttribute("startDate", startDate);
        model.addAttribute("businessRegistrationNumber", businessRegistrationNumber);
    }

    private String signupErrorMessage(String accountType, String message) {
        if ("owner".equals(accountType)) {
            return "회원가입이 완료되지 않았습니다. " + message;
        }
        return message;
    }
}
