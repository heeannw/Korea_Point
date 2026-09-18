package com.example.demo.user.controller;

import com.example.demo.user.exception.UserInputException;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.demo.config.CurrentLanguage;
import com.example.demo.config.LocalizedMessages;
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
import org.springframework.transaction.interceptor.TransactionAspectSupport;
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
    private final LocalizedMessages messages;

    // Spring Security OAuth2의 loginPage("/login")이 이 URL을 바라봄
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false, defaultValue = "false") boolean signup,
                            @RequestParam(required = false) String redirect,
                            HttpSession session, Model model) {
        // 로그인 화면도 사이트 언어를 그대로 쓴다. ?lang= 으로 오면 이미 세션에 들어가 있다.
        // 예전에는 주소에 lang 이 없으면 한국어로 떨어졌다.
        String selected = CurrentLanguage.get();
        if (isSafeRedirectPath(redirect)) {
            session.setAttribute("loginRedirectUrl", redirect);
        }
        model.addAttribute("lang", selected);
        model.addAttribute("signupMode", signup);
        return "user/login";
    }

    @GetMapping("/login/success")
    public String loginSuccess(@RequestParam(required = false) String provider,
                               HttpSession session,
                               Model model) {
        String selected = CurrentLanguage.get();
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
                redirectAttributes.addFlashAttribute("settingsMessage", messages.get("settings.flash.nationalitySaved"));
            }
        } catch (IllegalArgumentException ex) {
            if (!isSafeRedirectPath(redirect)) {
                redirectAttributes.addFlashAttribute("settingsError", messages.of(ex));
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
            redirectAttributes.addFlashAttribute("settingsMessage", messages.get("settings.flash.profileSaved"));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("settingsError", messages.of(ex));
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
            redirectAttributes.addFlashAttribute("passwordMessage", messages.get("settings.flash.passwordChanged"));
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("passwordError", messages.of(ex));
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
                throw new UserInputException("account.error.socialEmailReadonly", "소셜 로그인 계정은 이메일을 직접 변경할 수 없습니다.");
            }
            emailVerificationService.sendEmailChangeCode(session, newEmail);
            redirectAttributes.addFlashAttribute("emailMessage", messages.get("settings.flash.emailCodeSent"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("emailError", messages.of(ex));
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
            redirectAttributes.addFlashAttribute("emailMessage", messages.get("settings.flash.emailChanged"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("emailError", messages.of(ex));
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
            userService.withdrawAccount(SecurityUtil.getCurrentUserId(), currentPassword, confirmationText,
                    messages.get("settings.withdraw.confirmTextPlaceholder"));
            // 문구도 로그아웃 전에 만든다. 로그아웃하면 세션의 언어가 사라진다.
            redirectAttributes.addFlashAttribute("withdrawMessage", messages.get("settings.flash.withdrawn"));
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            // 로그아웃하면 세션이 비워져 언어도 함께 사라진다. 비우기 전에 읽어 두었다가 주소로 넘긴다.
            String lang = CurrentLanguage.get();
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/login?lang=" + lang;
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("withdrawError", messages.of(ex));
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
                         Model model) {
        String selected = CurrentLanguage.get();
        model.addAttribute("lang", selected);
        model.addAttribute("signupMode", true);
        model.addAttribute("accountType", accountType);

        try {
            if (!password.equals(passwordConfirm)) {
                throw new UserInputException("account.error.passwordMismatch", "비밀번호가 일치하지 않습니다.");
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
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // 이 메서드는 가입과 사업자 신청을 한 트랜잭션으로 묶는다. 안쪽 서비스에서 오류가 나면
            // 트랜잭션이 되돌리기로 표시되는데, 여기서 오류를 잡고 화면을 그리면 끝에서 커밋하려다
            // UnexpectedRollbackException 으로 500 이 났다(이미 있는 아이디로 가입할 때 등).
            // 되돌린다고 여기서 직접 표시하면 조용히 되돌리고 안내 화면이 나간다.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            restoreSignupForm(model, loginId, name, email, storeName, ownerName, startDate, businessRegistrationNumber);
            model.addAttribute("signupError", signupErrorMessage(accountType, messages.of(ex)));
            return "user/login";
        }
    }

    @PostMapping("/find-login-id")
    public String findLoginId(@RequestParam String name,
                              @RequestParam String email,
                              Model model) {
        String selected = CurrentLanguage.get();
        model.addAttribute("lang", selected);
        model.addAttribute("activePanel", "findIdPanel");
        try {
            String loginId = userService.findLocalLoginId(name, email)
                    .orElseThrow(() -> new UserInputException("account.error.loginIdNotFound", "일치하는 아이디를 찾을 수 없습니다."));
            model.addAttribute("findIdResult", messages.get("login.result.findId", loginId));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("findIdError", messages.of(ex));
        }
        return "user/login";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String loginId,
                                @RequestParam String email,
                                @RequestParam String newPassword,
                                @RequestParam String passwordConfirm,
                                Model model) {
        String selected = CurrentLanguage.get();
        model.addAttribute("lang", selected);
        model.addAttribute("activePanel", "resetPasswordPanel");
        try {
            userService.resetLocalPassword(loginId, email, newPassword, passwordConfirm);
            model.addAttribute("resetPasswordResult", messages.get("login.result.passwordReset"));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("resetPasswordError", messages.of(ex));
        }
        return "user/login";
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
            return messages.get("login.error.signupIncomplete", message);
        }
        return message;
    }
}
