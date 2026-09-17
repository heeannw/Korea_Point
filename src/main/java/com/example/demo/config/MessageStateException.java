package com.example.demo.config;

import com.example.demo.user.exception.CodedMessage;

/**
 * 지금 상태로는 처리할 수 없는 경우의 오류(권한 없음, 이미 신고함 등). 화면에는 문구 키를
 * 화면 언어로 바꿔 보여 준다({@link LocalizedMessages#of}). 국문 문장은 로그·테스트용.
 * 기존 코드가 {@link IllegalStateException} 으로 받던 자리를 그대로 쓸 수 있게 그 하위 클래스로 둔다.
 */
public class MessageStateException extends IllegalStateException implements CodedMessage {

    private final String code;
    private final Object[] args;

    public MessageStateException(String code, String koreanMessage, Object... args) {
        super(koreanMessage);
        this.code = code;
        this.args = args == null ? new Object[0] : args;
    }

    public MessageStateException(String code, String koreanMessage, Throwable cause) {
        super(koreanMessage, cause);
        this.code = code;
        this.args = new Object[0];
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public Object[] args() {
        return args.clone();
    }
}
