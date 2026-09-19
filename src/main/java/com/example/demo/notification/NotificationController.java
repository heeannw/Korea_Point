package com.example.demo.notification;

import com.example.demo.user.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;

    @GetMapping
    public NotificationService.Inbox list(@RequestParam(required = false) Long before) {
        return notifications.list(SecurityUtil.getCurrentUserId(), before);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> count() {
        return Map.of("count", notifications.unreadCount(SecurityUtil.getCurrentUserId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id) {
        notifications.markRead(SecurityUtil.getCurrentUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll() {
        notifications.markAllRead(SecurityUtil.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
