package com.example.demo.config;

import com.example.demo.user.exception.CodedMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * 컨트롤러가 화면에 넘길 안내·오류 문구를 지금 언어로 만든다.
 *
 * <p>플래시 메시지처럼 컨트롤러가 직접 문장을 만들어 넘기는 경우에 쓴다. 템플릿에서
 * {@code #{...}} 로 찍는 문구는 이걸 거칠 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class LocalizedMessages {

    private final MessageSource messageSource;

    /** 키로 문구를 찾는다. 키가 없으면 키 이름을 그대로 돌려 빠진 것이 눈에 띄게 한다. */
    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
    }

    /**
     * 오류를 화면 문구로. 이름표가 있으면 그 언어의 문구, 없으면 오류에 담긴 문장 그대로.
     * 이름표가 있는데 문구 파일에 키가 없으면 국문 문장으로 물러선다.
     */
    public String of(RuntimeException ex) {
        if (ex instanceof CodedMessage coded) {
            return messageSource.getMessage(coded.code(), coded.args(), ex.getMessage(), LocaleContextHolder.getLocale());
        }
        return ex.getMessage();
    }
}
