package com.example.demo.config;

import com.example.demo.user.exception.UserInputException;
import com.example.demo.user.exception.UserStateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원·설정 화면 안내문이 언어별로 제대로 만들어지는지 본다.
 * 문구 파일 설정은 application.properties 와 같게 둔다.
 */
class LocalizedMessagesTest {

    private LocalizedMessages messages;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        messages = new LocalizedMessages(source);
    }

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void 이름표가_있는_오류는_화면_언어로_바뀐다() {
        var ex = new UserInputException("account.error.passwordMismatch", "비밀번호가 일치하지 않습니다.");

        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertThat(messages.of(ex)).isEqualTo("パスワードが一致しません。");

        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(messages.of(ex)).isEqualTo("비밀번호가 일치하지 않습니다.");
    }

    @Test
    void 이름표가_없는_오류는_원래_문장이_나간다() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(messages.of(new IllegalArgumentException("그대로"))).isEqualTo("그대로");
    }

    @Test
    void 문구_파일에_없는_이름표면_국문_문장으로_물러선다() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var ex = new UserStateException("account.error.noSuchKey", "국문 문장");
        assertThat(messages.of(ex)).isEqualTo("국문 문장");
    }

    @Test
    void 값이_들어가는_오류도_채워진다() {
        var ex = new UserInputException("account.error.emailTaken", "이미 Google 로그인으로 가입된 이메일입니다.", "Google");
        LocaleContextHolder.setLocale(Locale.CHINESE);
        assertThat(messages.of(ex)).isEqualTo("该邮箱已通过Google注册。");
    }

    @Test
    void 불어_따옴표가_값이_있을_때도_살아_있다() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(messages.get("login.error.signupIncomplete", "X"))
                .isEqualTo("L'inscription n'est pas terminée. X");
        assertThat(messages.get("account.mail.body", "123456"))
                .contains("Code : 123456")
                .contains("Le code n'est valable que 5 minutes.");
    }

    @Test
    void 값이_없는_문구의_따옴표도_그대로다() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(messages.get("account.error.userNotFound")).isEqualTo("Cet utilisateur n'existe pas.");
    }

    @Test
    void 메일_본문은_줄바꿈이_살아_있다() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        assertThat(messages.get("account.mail.body", "123456"))
                .isEqualTo("KoreaPoint 이메일 변경 인증번호입니다.\n\n인증번호: 123456\n\n"
                        + "인증번호는 5분 동안만 사용할 수 있어요.\n메일이 보이지 않으면 스팸함도 확인해 주세요.\n");
    }

    @Test
    void 다섯_언어_모두_새_문구가_있다() {
        String[] keys = {"settings.flash.profileSaved", "login.result.passwordReset", "business.error.closed",
                "owner.error.alreadyPending", "mypage.flash.ownerApplied", "account.mail.subject"};
        for (Locale locale : new Locale[]{Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE, Locale.FRENCH}) {
            LocaleContextHolder.setLocale(locale);
            for (String key : keys) {
                assertThat(messages.get(key)).as("%s %s", locale, key).isNotEqualTo(key).isNotBlank();
            }
        }
    }
}
