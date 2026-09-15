package com.example.demo.community.entity;

/**
 * 신고 창에서 고를 수 있는 사유.
 *
 * <p>목록에 성적 콘텐츠와 자살·자해 자리가 없어서, 예전에는 신고 창이 그 둘을 SPAM 과
 * MISINFORMATION 에 끼워 넣고 스팸은 MISLEADING 으로 밀어 두었다. 화면 세 군데가
 * 같은 오해를 공유해 이용자와 관리자에게는 맞아 보였지만, DB 를 직접 열거나 통계를
 * 내는 사람은 SPAM 을 스팸으로 읽게 된다. 자리를 만들어 이름과 뜻을 맞춘다.
 *
 * <p>순서는 신고 창에 보이는 차례와 같다.
 */
public enum ContentReportReason {
    SEXUAL,
    ABUSE,
    HATE,
    HARASSMENT,
    HARMFUL,
    SELF_HARM,
    MISINFORMATION,
    CHILD_ABUSE,
    TERRORISM,
    SPAM,
    OTHER
}
