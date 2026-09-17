package com.example.demo.user.exception;

/**
 * 화면에 보여 줄 문구의 이름표를 가진 오류.
 *
 * <p>회원·사업자 서비스의 오류 문구는 한국어 문장으로 만들어져 화면까지 그대로 올라갔다.
 * 그래서 일본어로 보던 사람도 "비밀번호가 일치하지 않습니다" 를 봤다. 문장은 그대로 두고
 * (로그·테스트가 쓴다) 이름표를 붙여, 화면으로 보낼 때 언어별 문구로 바꾼다.
 * 바꾸는 곳은 {@link com.example.demo.config.LocalizedMessages}.
 */
public interface CodedMessage {

    /** messages*.properties 의 키. */
    String code();

    /** 문구의 {0}, {1} … 자리에 들어갈 값. 없으면 빈 배열. */
    default Object[] args() {
        return new Object[0];
    }
}
