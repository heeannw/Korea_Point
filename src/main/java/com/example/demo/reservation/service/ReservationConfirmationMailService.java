package com.example.demo.reservation.service;

import com.example.demo.reservation.event.ReservationConfirmedEvent;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationConfirmationMailService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(ReservationConfirmedEvent event) {
        if (!StringUtils.hasText(senderEmail)) {
            log.warn("Reservation confirmation mail skipped: spring.mail.username is empty");
            return;
        }

        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null || !hasDeliverableEmail(user.getEmail())) {
            log.warn("Reservation confirmation mail skipped: deliverable recipient not found, userId={}", event.userId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(senderEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("[KoreaPoint] 예약이 완료되었습니다 - " + event.placeTitle());
            helper.setText(html(user.getName(), event), true);
            mailSender.send(message);
            log.info("Reservation confirmation mail sent: reservationNo={}, userId={}",
                    event.reservationNo(), event.userId());
        } catch (Exception ex) {
            // 메일 장애가 이미 완료된 결제와 예약 상태에 영향을 주지 않도록 예외를 전파하지 않는다.
            log.error("Reservation confirmation mail failed: reservationNo={}, userId={}",
                    event.reservationNo(), event.userId(), ex);
        }
    }

    private boolean hasDeliverableEmail(String email) {
        return StringUtils.hasText(email) && !email.toLowerCase(Locale.ROOT).endsWith(".oauth.local");
    }

    private String html(String userName, ReservationConfirmedEvent event) {
        String name = HtmlUtils.htmlEscape(StringUtils.hasText(userName) ? userName : "고객");
        String place = HtmlUtils.htmlEscape(event.placeTitle());
        String reservationNo = HtmlUtils.htmlEscape(event.reservationNo());
        String time = HtmlUtils.htmlEscape(event.reservationTime());
        String amount = NumberFormat.getNumberInstance(Locale.KOREA).format(event.depositAmount());
        return """
                <!doctype html>
                <html lang="ko">
                <body style="margin:0;background:#f4f9ff;font-family:Arial,'Noto Sans KR',sans-serif;color:#16181d;">
                  <div style="max-width:620px;margin:0 auto;padding:32px 16px;">
                    <div style="background:#ffffff;border:1px solid #e8eaed;border-radius:16px;overflow:hidden;">
                      <div style="padding:24px 28px;background:#3b9eff;color:#ffffff;">
                        <div style="font-size:24px;font-weight:800;">KoreaPoint</div>
                        <div style="margin-top:8px;font-size:18px;">예약이 완료되었습니다.</div>
                      </div>
                      <div style="padding:28px;">
                        <p style="margin:0 0 22px;line-height:1.7;">%s님, 예약금 결제가 완료되어 아래 일정으로 예약되었습니다.</p>
                        <table style="width:100%%;border-collapse:collapse;line-height:1.6;">
                          <tr><th style="padding:10px;text-align:left;border-bottom:1px solid #e8eaed;color:#627d98;">예약번호</th><td style="padding:10px;border-bottom:1px solid #e8eaed;font-weight:700;">%s</td></tr>
                          <tr><th style="padding:10px;text-align:left;border-bottom:1px solid #e8eaed;color:#627d98;">장소</th><td style="padding:10px;border-bottom:1px solid #e8eaed;font-weight:700;">%s</td></tr>
                          <tr><th style="padding:10px;text-align:left;border-bottom:1px solid #e8eaed;color:#627d98;">방문일</th><td style="padding:10px;border-bottom:1px solid #e8eaed;">%s %s</td></tr>
                          <tr><th style="padding:10px;text-align:left;border-bottom:1px solid #e8eaed;color:#627d98;">인원</th><td style="padding:10px;border-bottom:1px solid #e8eaed;">%d명</td></tr>
                          <tr><th style="padding:10px;text-align:left;color:#627d98;">예약금</th><td style="padding:10px;font-weight:700;color:#1a7de0;">%s원</td></tr>
                        </table>
                        <p style="margin:24px 0 0;color:#627d98;font-size:13px;line-height:1.7;">예약 내역은 KoreaPoint 마이페이지에서 확인할 수 있습니다.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, reservationNo, place, event.reservationDate().format(DATE_FORMAT),
                time, event.peopleCount(), amount);
    }
}
