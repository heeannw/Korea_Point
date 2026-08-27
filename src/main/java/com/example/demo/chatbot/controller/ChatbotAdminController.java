package com.example.demo.chatbot.controller;

import com.example.demo.chatbot.service.ChatbotIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챗봇 벡터 인덱스를 수동으로 다시 채우는 관리용 엔드포인트.
 * TODO: 지금은 시큐리티 설정이 없어 공개 상태다(SecurityConfig는 다른 팀원 소유라 직접 안 건드림) —
 *       배포 전에 관리자 인증을 붙여야 한다.
 */
@RestController
public class ChatbotAdminController {

    private final ChatbotIngestionService ingestionService;

    public ChatbotAdminController(ChatbotIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/chatbot/admin/ingest/busan-story")
    public ChatbotIngestionService.IngestResult ingestBusanStories() {
        return ingestionService.ingestBusanStories();
    }
}
