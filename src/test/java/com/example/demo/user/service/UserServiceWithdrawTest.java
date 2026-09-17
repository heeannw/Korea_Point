package com.example.demo.user.service;

import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.exception.CodedMessage;
import com.example.demo.user.exception.UserInputException;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 소셜 계정 탈퇴 확인 문구.
 *
 * <p>예전엔 국문 "탈퇴합니다" 만 받았다. 일본어 화면은 「削除します」 를 입력하라고 안내해서,
 * 안내대로 입력한 사람은 탈퇴할 수 없었다.
 */
class UserServiceWithdrawTest {

    private UserRepository userRepository;
    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository, mock(PasswordEncoder.class), mock(MessageSource.class));
        user = User.builder().id(7L).provider("google").providerId("g-7").name("tester")
                .email("t@example.com").role("USER").build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
    }

    @Test
    void 화면_언어의_확인_문구로_탈퇴된다() {
        userService.withdrawAccount(7L, null, "削除します", "削除します");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(user.getProvider()).isEqualTo("withdrawn");
    }

    @Test
    void 영문_확인_문구는_대소문자를_가리지_않는다() {
        userService.withdrawAccount(7L, null, "delete", "DELETE");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void 국문_확인_문구는_어느_언어에서나_받는다() {
        userService.withdrawAccount(7L, null, "탈퇴합니다", "SUPPRIMER");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
    }

    @Test
    void 다른_문구면_막고_이름표를_붙인다() {
        assertThatThrownBy(() -> userService.withdrawAccount(7L, null, "wrong", "削除します"))
                .isInstanceOf(UserInputException.class)
                .hasMessage("확인 문구가 일치하지 않습니다.")
                .satisfies(ex -> assertThat(((CodedMessage) ex).code()).isEqualTo("account.error.confirmTextMismatch"));
        assertThat(user.getProvider()).isEqualTo("google");
    }
}
