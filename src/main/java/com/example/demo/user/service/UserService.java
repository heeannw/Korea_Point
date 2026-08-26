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

        return userRepository.save(User.builder()
                .provider("local")
                .providerId(normalizedLoginId)
                .password(passwordEncoder.encode(normalizedPassword))
                .name(requireText(name, "이름을 입력해 주세요."))
                .email(requireText(email, "이메일을 입력해 주세요."))
                .role("USER")
                .point(0)
                .build());
    }

    @Transactional(readOnly = true)
    public Optional<User> findLocalUserByLoginId(String loginId) {
        return userRepository.findByProviderAndProviderId("local", loginId);
    }

    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
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

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
