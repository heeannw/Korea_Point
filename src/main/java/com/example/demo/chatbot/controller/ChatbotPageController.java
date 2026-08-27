package com.example.demo.chatbot.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 독립된 챗봇 채팅 페이지. 다른 페이지 레이아웃과 무관하게 그 자체로 완결된 화면이다. */
@Controller
public class ChatbotPageController {

    @GetMapping("/chatbot")
    public String chatPage() {
        return "chatbot/chat";
    }
}
