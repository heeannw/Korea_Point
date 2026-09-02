package com.example.demo.chatbot.controller;

import com.example.demo.user.util.SecurityUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 독립된 챗봇 채팅 페이지. 헤더/번역 설정은 다른 페이지(관광 페이지)와 동일하게 맞춘다. */
@Controller
public class ChatbotPageController {

    @GetMapping("/chatbot")
    public String chatPage(Model model) {
        model.addAttribute("isLoggedIn", SecurityUtil.isLoggedIn());
        model.addAttribute("isAdmin", SecurityUtil.isAdmin());
        model.addAttribute("currentUserName", SecurityUtil.getCurrentUserName());
        return "chatbot/chat";
    }
}
