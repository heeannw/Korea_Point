package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.ReservationFormDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationFormDateTest {
    @ParameterizedTest
    @ValueSource(strings = {"ko", "en", "ja", "zh-CN", "fr"})
    void calendarDateRendersAndBindsAsIsoInEverySupportedLanguage(String language) throws Exception {
        var conversion = new DefaultFormattingConversionService();
        var dateField = new TypeDescriptor(ReservationFormDto.class.getDeclaredField("reservationDate"));
        var text = TypeDescriptor.valueOf(String.class);
        var date = LocalDate.of(2026, 9, 10);
        try {
            LocaleContextHolder.setLocale(Locale.forLanguageTag(language));
            assertThat(conversion.convert(date, dateField, text)).isEqualTo("2026-09-10");
            assertThat(conversion.convert("2026-09-10", text, dateField)).isEqualTo(date);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}