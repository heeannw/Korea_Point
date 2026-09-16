package com.example.demo.config;

import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Set;

/**
 * 지금 화면에 쓸 언어. 사이트 전체가 이 한 곳에서 언어를 읽는다.
 *
 * <p>언어는 세션에만 둔다. 주소에 {@code ?lang=ja} 가 붙어 오면 {@code LocaleChangeInterceptor} 가
 * 컨트롤러보다 먼저 세션에 넣어 주므로, 컨트롤러는 세션만 보면 주소로 온 값까지 함께 반영된다.
 *
 * <p>예전에는 화면마다 {@code @RequestParam(defaultValue = "ko") String lang} 으로 주소에서 언어를
 * 읽었다. 그러면 링크 하나에 lang 이 빠지는 순간 그 화면이 한국어로 뜨고, 그 화면 헤더의 링크가
 * 전부 {@code ?lang=ko} 로 만들어져 저장된 언어까지 한국어로 덮어썼다. 일본어로 보다가 예약을
 * 누르면 그 뒤로 사이트 전체가 한국어가 됐다.
 */
public final class CurrentLanguage {

    public static final String KO = "ko";
    public static final String EN = "en";

    private static final Set<String> SUPPORTED = Set.of("ko", "en", "ja", "zh", "fr");

    private CurrentLanguage() {
    }

    /** ko, en, ja, zh, fr 중 하나. 모르는 언어는 한국어로 본다. */
    public static String get() {
        return of(LocaleContextHolder.getLocale());
    }

    public static String of(Locale locale) {
        return normalize(locale == null ? null : locale.getLanguage());
    }

    public static String normalize(String language) {
        if (language == null) return KO;
        String lower = language.trim().toLowerCase(Locale.ROOT);
        int cut = lower.indexOf('-') >= 0 ? lower.indexOf('-') : lower.indexOf('_');
        if (cut > 0) lower = lower.substring(0, cut);
        return SUPPORTED.contains(lower) ? lower : KO;
    }
}
