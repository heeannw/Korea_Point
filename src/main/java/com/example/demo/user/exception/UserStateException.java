package com.example.demo.user.exception;

/**
 * 지금 상태로는 처리할 수 없는 경우(이미 신청함, 메일 발송 실패 등).
 * 기존 코드가 {@link IllegalStateException} 으로 받던 자리를 그대로 쓸 수 있게 그 하위 클래스로 둔다.
 */
public class UserStateException extends IllegalStateException implements CodedMessage {

    private final String code;

    public UserStateException(String code, String koreanMessage) {
        super(koreanMessage);
        this.code = code;
    }

    public UserStateException(String code, String koreanMessage, Throwable cause) {
        super(koreanMessage, cause);
        this.code = code;
    }

    @Override
    public String code() {
        return code;
    }
}
