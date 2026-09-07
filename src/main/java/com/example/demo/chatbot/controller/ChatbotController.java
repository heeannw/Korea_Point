package com.example.demo.chatbot.controller;

import com.example.demo.chatbot.dto.ChatAskRequest;
import com.example.demo.chatbot.dto.ChatAskResponse;
import com.example.demo.chatbot.service.ChatbotQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ChatbotController {

    /** 이미지 하나가 base64로 부풀려져 요청 본문에 그대로 실리므로(멀티파트가 아님) 너무 큰
     *  사진이 그대로 들어오지 않게 여기서도 한 번 더 막는다(프론트에서도 미리 막지만 이중 방어). */
    private static final int MAX_IMAGE_BASE64_CHARS = 8_000_000; // base64 약 6MB 상당

    private final ChatbotQueryService chatbotQueryService;

    public ChatbotController(ChatbotQueryService chatbotQueryService) {
        this.chatbotQueryService = chatbotQueryService;
    }

    @PostMapping("/chatbot/ask")
    public ChatAskResponse ask(@RequestBody ChatAskRequest request) {
        String question = request.question() == null ? "" : request.question().trim();
        boolean hasImage = request.image() != null && !request.image().isBlank();
        if (question.isEmpty() && !hasImage) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "질문이나 사진 중 하나는 있어야 합니다.");
        }
        if (hasImage && request.image().length() > MAX_IMAGE_BASE64_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "사진 용량이 너무 커요.");
        }
        return chatbotQueryService.ask(question, hasImage ? request.image() : null,
                hasImage ? request.imageMimeType() : null);
    }
}
