package com.example.demo.chatbot.client;

/** 챗봇이 호출하는 외부 API(부산 이야기, Gemini 임베딩 등)에서 발생한 오류. */
public class ChatbotApiException extends RuntimeException {
    public ChatbotApiException(String message) {
        super(message);
    }

    public ChatbotApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
