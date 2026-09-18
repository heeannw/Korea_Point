package com.example.demo.user.exception;

/**
 * 입력이 잘못된 경우. 기존 코드가 {@link IllegalArgumentException} 으로 받던 자리를 그대로 쓸 수 있게
 * 그 하위 클래스로 둔다.
 */
public class UserInputException extends IllegalArgumentException implements CodedMessage {

    private final String code;
    private final Object[] args;

    /**
     * @param code          화면 문구 키
     * @param koreanMessage 로그와 테스트가 보는 국문 문장. 키를 못 찾을 때도 이 문장이 나간다.
     */
    public UserInputException(String code, String koreanMessage, Object... args) {
        super(koreanMessage);
        this.code = code;
        this.args = args == null ? new Object[0] : args.clone();
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
