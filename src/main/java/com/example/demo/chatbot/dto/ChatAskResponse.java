package com.example.demo.chatbot.dto;

import java.util.List;

public record ChatAskResponse(String answer, List<Source> sources) {

    /**
     * 답변 근거로 쓴 이야기 하나. content는 이미 이번 요청에서 원문을 조회한 김에 같이 담아서,
     * 프론트에서 카드 클릭했을 때 추가 API 호출 없이 바로 전문을 보여줄 수 있게 한다.
     */
    public record Source(String title, String sourceType, String imgUrl, String content) {}
}
