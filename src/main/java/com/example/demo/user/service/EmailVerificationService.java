package com.example.demo.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String EMAIL_ATTRIBUTE = "emailChangeTarget";
    private static final String CODE_ATTRIBUTE = "emailChangeCode";
    private static final String EXPIRES_AT_ATTRIBUTE = "emailChangeExpiresAt";
    private static final long EXPIRES_IN_SECONDS = 300;

    private final JavaMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${spring.mail.username:}")
    private String senderEmail;

    public void sendEmailChangeCode(HttpSession session, String targetEmail) {
        if (!StringUtils.hasText(senderEmail)) {
            throw new IllegalStateException("메일 발송 계정이 설정되지 않았어요.");
        }

        String normalizedEmail = normalizeEmail(targetEmail);
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        sendMail(normalizedEmail, code);
        session.setAttribute(EMAIL_ATTRIBUTE, normalizedEmail);
        session.setAttribute(CODE_ATTRIBUTE, code);
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, Instant.now().plusSeconds(EXPIRES_IN_SECONDS));
    }

    public String getPendingEmail(HttpSession session) {
        Object email = session.getAttribute(EMAIL_ATTRIBUTE);
        Object expiresAt = session.getAttribute(EXPIRES_AT_ATTRIBUTE);
        if (email instanceof String value && expiresAt instanceof Instant expiration && Instant.now().isBefore(expiration)) {
            return value;
        }
        clearEmailChangeCode(session);
        return null;
    }

    public String verifyEmailChangeCode(HttpSession session, String targetEmail, String code) {
        String normalizedEmail = normalizeEmail(targetEmail);
        Object savedEmail = session.getAttribute(EMAIL_ATTRIBUTE);
        Object savedCode = session.getAttribute(CODE_ATTRIBUTE);
        Object expiresAt = session.getAttribute(EXPIRES_AT_ATTRIBUTE);

        if (!(expiresAt instanceof Instant expiration) || Instant.now().isAfter(expiration)) {
            clearEmailChangeCode(session);
            throw new IllegalArgumentException("인증번호가 만료됐어요. 다시 발급해 주세요.");
        }
        if (!Objects.equals(savedEmail, normalizedEmail) || !Objects.equals(savedCode, code)) {
            throw new IllegalArgumentException("인증번호가 일치하지 않습니다.");
        }

        clearEmailChangeCode(session);
        return normalizedEmail;
    }

    private void sendMail(String targetEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(targetEmail);
            helper.setSubject("[KoreaPoint] 이메일 인증번호");
            helper.setText("""
                    KoreaPoint 이메일 변경 인증번호입니다.

                    인증번호: %s

                    인증번호는 5분 동안만 사용할 수 있어요.
                    메일이 보이지 않으면 스팸함도 확인해 주세요.
                    """.formatted(code), false);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("인증 메일을 만들지 못했어요.", ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("인증 메일 발송에 실패했어요. 메일 설정을 확인해 주세요.", ex);
        }
    }

    private String normalizeEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("이메일을 입력해 주세요.");
        }
        return email.trim().toLowerCase();
    }

    private void clearEmailChangeCode(HttpSession session) {
        session.removeAttribute(EMAIL_ATTRIBUTE);
        session.removeAttribute(CODE_ATTRIBUTE);
        session.removeAttribute(EXPIRES_AT_ATTRIBUTE);
    }
}
