package com.example.demo.chatbot.controller;

import com.example.demo.chatbot.service.ChatbotIngestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 챗봇 벡터 인덱스를 수동으로 다시 채우는 관리용 엔드포인트.
 * 경로를 /admin/** 아래에 둬서 SecurityConfig의 기존 규칙(hasRole("ADMIN"))을
 * 그대로 물려받는다 — SecurityConfig 자체는 건드리지 않았다.
 */
@RestController
public class ChatbotAdminController {

    private final ChatbotIngestionService ingestionService;

    public ChatbotAdminController(ChatbotIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/admin/chatbot/ingest/busan-story")
    public ChatbotIngestionService.IngestResult ingestBusanStories() {
        return ingestionService.ingestBusanStories();
    }
}
