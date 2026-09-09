package com.example.demo.tourism.service;

import com.example.demo.tourism.entity.ActivityType;
import com.example.demo.tourism.entity.UserActivity;
import com.example.demo.tourism.repository.UserActivityRepository;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인화 추천용 사용자 행동 로그 저장. 실패해도 화면을 막지 않는다(삼킨다).
 * 저장 자체가 인덱스 하나 붙은 INSERT 한 건이라 동기로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final UserActivityRepository activityRepository;
    private final UserRepository userRepository;

    /** 프론트가 보내는 행동 한 건. 액션별로 채워지는 필드가 다르다. */
    public record ActivityRequest(String action, String contentId, String contentTypeId,
                                  String categorySlug, String keyword, String area) { }

    @Transactional
    public void record(Long userId, ActivityRequest req) {
        if (userId == null || req == null || req.action() == null) return;
        ActivityType type;
        try {
            type = ActivityType.valueOf(req.action().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return;   // 모르는 액션이면 무시
        }
        try {
            activityRepository.save(UserActivity.builder()
                    .user(userRepository.getReferenceById(userId))
                    .action(type)
                    .contentId(trim(req.contentId(), 20))
                    .contentTypeId(trim(req.contentTypeId(), 5))
                    .categorySlug(trim(req.categorySlug(), 40))
                    .keyword(trim(req.keyword(), 100))
                    .area(trim(req.area(), 20))
                    .build());
        } catch (Exception e) {
            log.debug("행동 로그 저장 실패 (무시) userId={} action={}", userId, type, e);
        }
    }

    /** "부산광역시 해운대구 우동 …" → "해운대구". 못 뽑으면 null. */
    public static String areaFromAddr(String addr) {
        if (addr == null) return null;
        for (String tok : addr.split("\\s+")) {
            if (tok.endsWith("구") || tok.endsWith("군")) return tok;
        }
        return null;
    }

    private static String trim(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
