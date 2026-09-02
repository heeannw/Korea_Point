package com.example.demo.user.service;

import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
                                .point(0)
                                .build()
                ));
    }

    @Transactional
    public User registerLocalUser(String loginId, String password, String name, String email) {
        String normalizedLoginId = requireText(loginId, "아이디를 입력해 주세요.");
        String normalizedPassword = requireText(password, "비밀번호를 입력해 주세요.");
        validateLoginId(normalizedLoginId);
        validatePassword(normalizedPassword);

        if (userRepository.existsByProviderAndProviderId("local", normalizedLoginId)) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        validateEmailAvailableForLocalSignup(requireText(email, "이메일을 입력해 주세요."));

        return userRepository.save(User.builder()
                .provider("local")
                .providerId(normalizedLoginId)
                .password(passwordEncoder.encode(normalizedPassword))
                .name(requireText(name, "이름을 입력해 주세요."))
                .email(requireText(email, "이메일을 입력해 주세요."))
                .nationalityPrompted(false)
                .role("USER")
                .point(0)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<User> findLocalUserByLoginId(String loginId) {
        return userRepository.findByProviderAndProviderId("local", loginId)
                .filter(user -> !isWithdrawn(user));
    }

    @Transactional(readOnly = true)
    public Optional<String> findLocalLoginId(String name, String email) {
        String normalizedName = requireText(name, "이름을 입력해 주세요.");
        String normalizedEmail = requireText(email, "이메일을 입력해 주세요.");

        return userRepository.findByProviderAndEmailIgnoreCase("local", normalizedEmail).stream()
                .filter(user -> normalizedName.equals(user.getName()))
                .map(User::getProviderId)
                .findFirst();
    }

    @Transactional
    public void resetLocalPassword(String loginId, String email, String newPassword, String passwordConfirm) {
        String normalizedLoginId = requireText(loginId, "아이디를 입력해 주세요.");
        String normalizedEmail = requireText(email, "이메일을 입력해 주세요.");
        String normalizedPassword = requireText(newPassword, "새 비밀번호를 입력해 주세요.");
        if (!normalizedPassword.equals(passwordConfirm)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        validatePassword(normalizedPassword);

        User user = userRepository.findByProviderAndProviderId("local", normalizedLoginId)
                .filter(found -> normalizedEmail.equalsIgnoreCase(found.getEmail()))
                .orElseThrow(() -> new IllegalArgumentException("일치하는 계정을 찾을 수 없습니다."));
        user.setPassword(passwordEncoder.encode(normalizedPassword));
    }

    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    public boolean hasProvidedEmail(User user) {
        return user != null
                && StringUtils.hasText(user.getEmail())
                && !user.getEmail().endsWith(".oauth.local");
    }

    public String loginProviderName(User user) {
        if (user == null || !StringUtils.hasText(user.getProvider())) {
            return "알 수 없음";
        }
        return switch (user.getProvider()) {
            case "local" -> "아이디 로그인";
            case "google" -> "Google 로그인";
            case "naver" -> "네이버 로그인";
            case "kakao" -> "카카오 로그인";
            default -> "소셜 로그인";
        };
    }

    @Transactional
    public void updateProfile(Long userId, String name) {
        User user = findById(userId);
        user.setName(requireText(name, "이름을 입력해 주세요."));
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
            throw new IllegalArgumentException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        String normalizedCurrentPassword = requireText(currentPassword, "현재 비밀번호를 입력해 주세요.");
        String normalizedNewPassword = requireText(newPassword, "새 비밀번호를 입력해 주세요.");
        if (!passwordEncoder.matches(normalizedCurrentPassword, user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        if (!normalizedNewPassword.equals(passwordConfirm)) {
            throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다.");
        }
        if (passwordEncoder.matches(normalizedNewPassword, user.getPassword())) {
            throw new IllegalArgumentException("기존 비밀번호와 같은 비밀번호는 사용할 수 없습니다.");
        }
        validatePassword(normalizedNewPassword);
        user.setPassword(passwordEncoder.encode(normalizedNewPassword));
    }

    @Transactional
    public void updateLocalEmail(Long userId, String email) {
        User user = findById(userId);
        if (!"local".equals(user.getProvider())) {
            throw new IllegalArgumentException("소셜 로그인 계정은 이메일을 직접 변경할 수 없습니다.");
        }
        String normalizedEmail = requireText(email, "이메일을 입력해 주세요.");
        validateEmailAvailableForLocalSignup(normalizedEmail);
        user.setEmail(normalizedEmail);
    }

    @Transactional
    public void withdrawAccount(Long userId, String currentPassword, String confirmationText) {
        User user = findById(userId);
        if (isWithdrawn(user)) {
            throw new IllegalArgumentException("이미 탈퇴한 계정입니다.");
        }

        if ("local".equals(user.getProvider())) {
            String normalizedCurrentPassword = requireText(currentPassword, "현재 비밀번호를 입력해 주세요.");
            if (!passwordEncoder.matches(normalizedCurrentPassword, user.getPassword())) {
                throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
            }
        } else if (!"탈퇴합니다".equals(requireText(confirmationText, "확인 문구를 입력해 주세요."))) {
            throw new IllegalArgumentException("확인 문구가 일치하지 않습니다.");
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
            throw new IllegalArgumentException("아이디는 영문과 숫자를 포함한 4~20자로 입력해 주세요.");
        }
    }

    private void validatePassword(String password) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자로 입력해 주세요.");
        }
    }

    private void validateEmailAvailableForLocalSignup(String email) {
        userRepository.findByEmail(email).stream()
                .filter(user -> !isWithdrawn(user))
                .findFirst()
                .ifPresent(user -> {
                    throw new IllegalArgumentException(
                            "이미 " + loginProviderName(user) + "으로 가입된 이메일입니다."
                    );
                });
    }

    private boolean isWithdrawn(User user) {
        return user != null && "withdrawn".equals(user.getProvider());
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNationality(String nationality) {
        if (!StringUtils.hasText(nationality)) {
            return null;
        }
        String normalized = nationality.trim().toUpperCase();
        if (!normalized.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("국적을 다시 선택해 주세요.");
        }
        return normalized;
    }
}
