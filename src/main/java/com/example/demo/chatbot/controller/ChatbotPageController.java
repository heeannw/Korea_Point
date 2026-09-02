package com.example.demo.chatbot.controller;

import com.example.demo.user.util.SecurityUtil;
import org.springframework.context.i18n.LocaleContextHolder;
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
        // 공용 헤더(fragments/app-header)가 요구하는 값 — ?lang= 로 세션에 저장된 현재 로케일을 그대로 넘긴다.
        model.addAttribute("lang", LocaleContextHolder.getLocale().getLanguage());
        return "chatbot/chat";
    }
}
