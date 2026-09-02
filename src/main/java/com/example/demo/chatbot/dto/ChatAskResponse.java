package com.example.demo.chatbot.dto;

import java.util.List;

public record ChatAskResponse(String answer, List<Source> sources) {

    /**
     * 답변 근거로 쓴 이야기 하나. content는 이미 이번 요청에서 원문을 조회한 김에 같이 담아서,
     * 프론트에서 카드 클릭했을 때 추가 API 호출 없이 바로 전문을 보여줄 수 있게 한다.
     * summary는 번역이 필요한(한국어가 아닌) 질문일 때만 채워진다 — 긴 원문을 그대로 펼치면
     * 가독성이 떨어진다는 피드백으로, 짧은 요약을 먼저 보여주고 원할 때만 전문을 펼치게 한다.
     * 한국어 질문일 땐 비어 있고, 프론트가 그럴 땐 글자 수 기준으로 알아서 접어서 보여준다.
     */
    public record Source(String title, String sourceType, String imgUrl, String content, String summary) {}
}
