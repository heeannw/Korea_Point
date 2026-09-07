package com.example.demo.chatbot.dto;

/**
 * question과 image는 최소 하나만 있으면 된다(사진만 보내고 질문 텍스트가 없는 경우를 허용하기
 * 위해 question에 @NotBlank를 걸지 않는다) — 실제 검증은 ChatbotController에서 한다.
 * image는 "data:image/png;base64,...." 접두어를 뗀 순수 base64 문자열, imageMimeType은
 * "image/png" 같은 MIME 타입이다. 이미지가 없으면 둘 다 null.
 */
public record ChatAskRequest(String question, String image, String imageMimeType) {
}
