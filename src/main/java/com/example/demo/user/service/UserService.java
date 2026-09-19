package com.example.demo.user.service;

import com.example.demo.user.exception.UserInputException;
import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern LOGIN_ID_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{4,20}$");
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,20}$");
    private static final int SIGNUP_BONUS_POINT = 5000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @Transactional
    public User processOAuth2User(String provider, String providerId,
                                  String email, String name, String profileImage) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .provider(provider)
                                .providerId(providerId)
                                .email(email)
                                .name(name)
                                .profileImage(profileImage)
                                .nationalityPrompted(false)
                                .point(SIGNUP_BONUS_POINT)
                                .build()
                ));
    }

    @Transactional
    public User registerLocalUser(String loginId, String password, String name, String email) {
        String normalizedLoginId = requireText(loginId, "account.error.loginIdRequired", "아이디를 입력해 주세요.");
        String normalizedPassword = requireText(password, "account.error.passwordRequired", "비밀번호를 입력해 주세요.");
        validateLoginId(normalizedLoginId);
        validatePassword(normalizedPassword);

        if (userRepository.existsByProviderAndProviderId("local", normalizedLoginId)) {
            throw new UserInputException("account.error.loginIdTaken", "이미 사용 중인 아이디입니다.");
        }
        validateEmailAvailableForLocalSignup(requireText(email, "account.error.emailRequired", "이메일을 입력해 주세요."));

        return userRepository.save(User.builder()
                .provider("local")
                .providerId(normalizedLoginId)
                .password(passwordEncoder.encode(normalizedPassword))
                .name(requireText(name, "account.error.nameRequired", "이름을 입력해 주세요."))
                .email(requireText(email, "account.error.emailRequired", "이메일을 입력해 주세요."))
                .nationalityPrompted(false)
                .role("USER")
                .point(SIGNUP_BONUS_POINT)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<User> findLocalUserByLoginId(String loginId) {
        return userRepository.findByProviderAndProviderId("local", loginId)
                .filter(user -> !isWithdrawn(user));
    }

    @Transactional(readOnly = true)
    public Optional<String> findLocalLoginId(String name, String email) {
        String normalizedName = requireText(name, "account.error.nameRequired", "이름을 입력해 주세요.");
        String normalizedEmail = requireText(email, "account.error.emailRequired", "이메일을 입력해 주세요.");

        return userRepository.findByProviderAndEmailIgnoreCase("local", normalizedEmail).stream()
                .filter(user -> normalizedName.equals(user.getName()))
                .map(User::getProviderId)
                .findFirst();
    }

    @Transactional
    public void resetLocalPassword(String loginId, String email, String newPassword, String passwordConfirm) {
        String normalizedLoginId = requireText(loginId, "account.error.loginIdRequired", "아이디를 입력해 주세요.");
        String normalizedEmail = requireText(email, "account.error.emailRequired", "이메일을 입력해 주세요.");
        String normalizedPassword = requireText(newPassword, "account.error.newPasswordRequired", "새 비밀번호를 입력해 주세요.");
        if (!normalizedPassword.equals(passwordConfirm)) {
            throw new UserInputException("account.error.passwordMismatch", "비밀번호가 일치하지 않습니다.");
        }
        validatePassword(normalizedPassword);

        User user = userRepository.findByProviderAndProviderId("local", normalizedLoginId)
                .filter(found -> normalizedEmail.equalsIgnoreCase(found.getEmail()))
                .orElseThrow(() -> new UserInputException("account.error.accountNotFound", "일치하는 계정을 찾을 수 없습니다."));
        user.setPassword(passwordEncoder.encode(normalizedPassword));
    }

    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserInputException("account.error.userNotFound", "존재하지 않는 사용자입니다."));
    }

    public boolean hasProvidedEmail(User user) {
        return user != null
                && StringUtils.hasText(user.getEmail())
                && !user.getEmail().endsWith(".oauth.local");
    }

    public String loginProviderName(User user) {
        if (user == null || !StringUtils.hasText(user.getProvider())) {
            return message("user.provider.unknown", "알 수 없음");
        }
        String provider = user.getProvider();
        String fallback = switch (provider) {
            case "local" -> "아이디 로그인";
            case "google" -> "Google 로그인";
            case "naver" -> "네이버 로그인";
            case "kakao" -> "카카오 로그인";
            default -> "소셜 로그인";
        };
        return message("user.provider." + provider, fallback);
    }

    private String message(String key, String fallback) {
        return messageSource.getMessage(key, null, fallback, LocaleContextHolder.getLocale());
    }

    @Transactional
    public void updateProfile(Long userId, String name) {
        User user = findById(userId);
        user.setName(requireText(name, "account.error.nameRequired", "이름을 입력해 주세요."));
    }

    @Transactional
    public void updateNationality(Long userId, String nationality) {
        User user = findById(userId);
        user.setNationality(normalizeNationality(nationality));
        user.setNationalityPrompted(true);
    }

    @Transactional
    public void skipNationalityPrompt(Long userId) {
        User user = findById(userId);
        user.setNationalityPrompted(true);
    }

    @Transactional(readOnly = true)
    public boolean shouldShowNationalityPrompt(Long userId) {
        User user = findById(userId);
        return Boolean.FALSE.equals(user.getNationalityPrompted());
    }

    @Transactional
    public void changeLocalPassword(Long userId, String currentPassword,
                                    String newPassword, String passwordConfirm) {
        User user = findById(userId);
        if (!"local".equals(user.getProvider())) {
            throw new UserInputException("account.error.socialPasswordReadonly", "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        String normalizedCurrentPassword = requireText(currentPassword, "account.error.currentPasswordRequired", "현재 비밀번호를 입력해 주세요.");
        String normalizedNewPassword = requireText(newPassword, "account.error.newPasswordRequired", "새 비밀번호를 입력해 주세요.");
        if (!passwordEncoder.matches(normalizedCurrentPassword, user.getPassword())) {
            throw new UserInputException("account.error.currentPasswordMismatch", "현재 비밀번호가 일치하지 않습니다.");
        }
        if (!normalizedNewPassword.equals(passwordConfirm)) {
            throw new UserInputException("account.error.newPasswordMismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(normalizedNewPassword, user.getPassword())) {
            throw new UserInputException("account.error.samePassword", "기존 비밀번호와 같은 비밀번호는 사용할 수 없습니다.");
        }
        validatePassword(normalizedNewPassword);
        user.setPassword(passwordEncoder.encode(normalizedNewPassword));
    }

    @Transactional
    public void updateLocalEmail(Long userId, String email) {
        User user = findById(userId);
        if (!"local".equals(user.getProvider())) {
            throw new UserInputException("account.error.socialEmailReadonly", "소셜 로그인 계정은 이메일을 직접 변경할 수 없습니다.");
        }
        String normalizedEmail = requireText(email, "account.error.emailRequired", "이메일을 입력해 주세요.");
        validateEmailAvailableForLocalSignup(normalizedEmail);
        user.setEmail(normalizedEmail);
    }

    /** 소셜 계정 탈퇴 때 입력해야 하는 국문 확인 문구. 화면 언어의 문구와 함께 늘 받는다. */
    private static final String WITHDRAW_PHRASE_KO = "탈퇴합니다";

    @Transactional
    public void withdrawAccount(Long userId, String currentPassword, String confirmationText) {
        withdrawAccount(userId, currentPassword, confirmationText, null);
    }

    /**
     * @param localizedPhrase 화면에 안내한 확인 문구(일본어 화면이면 「削除します」).
     *                        예전엔 국문 "탈퇴합니다" 만 받아서, 화면 안내대로 입력한
     *                        다른 언어 사용자는 탈퇴할 수 없었다.
     */
    @Transactional
    public void withdrawAccount(Long userId, String currentPassword, String confirmationText, String localizedPhrase) {
        User user = findById(userId);
        if (isWithdrawn(user)) {
            throw new UserInputException("account.error.alreadyWithdrawn", "이미 탈퇴한 계정입니다.");
        }

        if ("local".equals(user.getProvider())) {
            String normalizedCurrentPassword = requireText(currentPassword, "account.error.currentPasswordRequired", "현재 비밀번호를 입력해 주세요.");
            if (!passwordEncoder.matches(normalizedCurrentPassword, user.getPassword())) {
                throw new UserInputException("account.error.currentPasswordMismatch", "현재 비밀번호가 일치하지 않습니다.");
            }
        } else if (!isWithdrawPhrase(
                requireText(confirmationText, "account.error.confirmTextRequired", "확인 문구를 입력해 주세요."),
                localizedPhrase)) {
            throw new UserInputException("account.error.confirmTextMismatch", "확인 문구가 일치하지 않습니다.");
        }

        user.setAccountStatus(AccountStatus.SUSPENDED);
        user.setProvider("withdrawn");
        user.setProviderId("withdrawn:" + user.getId());
        user.setName("탈퇴한 사용자");
        user.setEmail("withdrawn:" + user.getId() + "@koreapoint.local");
        user.setPassword(null);
        user.setProfileImage(null);
        user.setRole("USER");
        user.setPoint(0);
    }

    @Transactional(readOnly = true)
    public boolean isCommunitySuspended(Long userId) {
        return findById(userId).getAccountStatus() == AccountStatus.SUSPENDED;
    }

    private void validateLoginId(String loginId) {
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new UserInputException("account.error.loginIdRule", "아이디는 영문과 숫자를 포함한 4~20자로 입력해 주세요.");
        }
    }

    private void validatePassword(String password) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new UserInputException("account.error.passwordRule", "비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자로 입력해 주세요.");
        }
    }

    private void validateEmailAvailableForLocalSignup(String email) {
        userRepository.findByEmail(email).stream()
                .filter(user -> !isWithdrawn(user))
                .findFirst()
                .ifPresent(user -> {
                    // 로그인 방식 이름은 이미 지금 언어로 나온다(loginProviderName).
                    String provider = loginProviderName(user);
                    throw new UserInputException("account.error.emailTaken",
                            "이미 " + provider + "으로 가입된 이메일입니다.", provider);
                });
    }

    private boolean isWithdrawn(User user) {
        return user != null && "withdrawn".equals(user.getProvider());
    }

    private static boolean isWithdrawPhrase(String typed, String localizedPhrase) {
        if (WITHDRAW_PHRASE_KO.equals(typed)) return true;
        return StringUtils.hasText(localizedPhrase) && localizedPhrase.trim().equalsIgnoreCase(typed);
    }

    private String requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new UserInputException(code, message);
        }
        return value.trim();
    }

    private String normalizeNationality(String nationality) {
        if (!StringUtils.hasText(nationality)) {
            return null;
        }
        String normalized = nationality.trim().toUpperCase();
        if (!normalized.matches("^[A-Z]{2}$")) {
            throw new UserInputException("account.error.nationalityInvalid", "국적을 다시 선택해 주세요.");
        }
        return normalized;
    }
}
