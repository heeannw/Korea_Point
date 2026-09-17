package com.example.demo.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentLanguageTest {

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void 지원하는_다섯_언어는_그대로다() {
        for (String lang : new String[]{"ko", "en", "ja", "zh", "fr"}) {
            assertThat(CurrentLanguage.normalize(lang)).isEqualTo(lang);
        }
    }

    @Test
    void 지역이_붙은_표기는_언어만_남긴다() {
        assertThat(CurrentLanguage.normalize("zh-CN")).isEqualTo("zh");
        assertThat(CurrentLanguage.normalize("en_US")).isEqualTo("en");
        assertThat(CurrentLanguage.normalize(" JA ")).isEqualTo("ja");
    }

    @Test
    void 모르는_언어와_빈_값은_한국어다() {
        assertThat(CurrentLanguage.normalize("de")).isEqualTo("ko");
        assertThat(CurrentLanguage.normalize("")).isEqualTo("ko");
        assertThat(CurrentLanguage.normalize(null)).isEqualTo("ko");
        assertThat(CurrentLanguage.of(null)).isEqualTo("ko");
    }

    @Test
    void 지금_요청의_언어를_읽는다() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        assertThat(CurrentLanguage.get()).isEqualTo("zh");
    }
}
