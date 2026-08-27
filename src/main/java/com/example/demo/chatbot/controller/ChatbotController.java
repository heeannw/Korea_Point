package com.example.demo.chatbot.controller;

import com.example.demo.chatbot.dto.ChatAskRequest;
import com.example.demo.chatbot.dto.ChatAskResponse;
import com.example.demo.chatbot.service.ChatbotQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatbotController {

    private final ChatbotQueryService chatbotQueryService;

    public ChatbotController(ChatbotQueryService chatbotQueryService) {
        this.chatbotQueryService = chatbotQueryService;
    }

    @PostMapping("/chatbot/ask")
    public ChatAskResponse ask(@Valid @RequestBody ChatAskRequest request) {
        return chatbotQueryService.ask(request.question());
    }
}
