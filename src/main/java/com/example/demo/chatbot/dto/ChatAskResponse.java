package com.example.demo.chatbot.dto;

import java.util.List;

public record ChatAskResponse(String answer, List<Source> sources) {

    /** 답변 근거로 쓴 이야기 하나. 원문은 응답에 담지 않고 제목만 인용 표시용으로 준다. */
    public record Source(String title, String sourceType) {}
}
