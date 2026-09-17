package com.example.demo.trip.service;

import com.example.demo.config.LocalizedMessages;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.transport.service.TransportNameTranslator;
import com.example.demo.trip.dto.TripRecommendResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TripDisplayTextTest {

    private TourPlaceI18n placeI18n;
    private TransportNameTranslator names;
    private TripDisplayText display;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        placeI18n = mock(TourPlaceI18n.class);
        names = mock(TransportNameTranslator.class);
        when(names.translateAll(anyCollection(), any())).thenReturn(Map.of());
        display = new TripDisplayText(placeI18n, names, new LocalizedMessages(source));
    }

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static TripRecommendResponse oneSlot() {
        var slot = new TripRecommendResponse.SlotDto();
        slot.setPlaceName("양정청소년수련관");
        slot.setContentId("131143");
        slot.setAddress("부산광역시 부산진구 동평로405번길 85");
        var day = new TripRecommendResponse.DayScheduleDto();
        day.setSlots(List.of(slot));
        var resp = new TripRecommendResponse();
        resp.setSchedule(List.of(day));
        return resp;
    }

    @Test
    void 국문_화면이면_손대지_않는다() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        TripRecommendResponse resp = oneSlot();
        resp.setWeatherWarning("부산 지역 기상특보가 발표되어 야외 활동 전 최신 정보를 확인하세요.");
        display.apply(resp);
        assertThat(resp.getDisplayText()).isNull();
        assertThat(resp.getWeatherWarning()).startsWith("부산 지역");
        assertThat(resp.getDisplayLang()).isNull();
        verifyNoInteractions(placeI18n);
    }

    @Test
    void 장소_이름과_주소는_표로_따로_싣고_원래_이름은_그대로_둔다() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        when(placeI18n.translateTitle("ja", "131143", "양정청소년수련관")).thenReturn("ヤンジョン青少年研修館");
        when(placeI18n.translateAddress("ja", "131143", "부산광역시 부산진구 동평로405번길 85"))
                .thenReturn("釜山広域市 釜山鎮区 東平路405番ギル 85");

        TripRecommendResponse resp = display.apply(oneSlot());

        assertThat(resp.getSchedule().get(0).getSlots().get(0).getPlaceName()).isEqualTo("양정청소년수련관");
        assertThat(resp.getDisplayLang()).isEqualTo("ja");
        assertThat(resp.getDisplayText())
                .containsEntry("양정청소년수련관", "ヤンジョン青少年研修館")
                .containsEntry("부산광역시 부산진구 동평로405번길 85", "釜山広域市 釜山鎮区 東平路405番ギル 85");
    }

    @Test
    void 날씨_특보와_단계도_원문은_두고_표에_싣는다() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
        TripRecommendResponse resp = new TripRecommendResponse();
        resp.setWeatherWarning("부산 지역 폭염 특보가 발표되어 야외 일정을 최소화했습니다.");
        var air = new TripRecommendResponse.AirQualitySummary();
        air.setGrade("매우 나쁨");
        resp.setAirQuality(air);
        var risk = new TripRecommendResponse.HealthRiskSummary();
        risk.setUvLevel("높음");
        risk.setHeatRisk("주의");
        resp.setHealthRisk(risk);

        display.apply(resp);

        assertThat(resp.getWeatherWarning()).startsWith("부산 지역 폭염");
        assertThat(air.getGrade()).isEqualTo("매우 나쁨");
        assertThat(resp.getDisplayText())
                .containsEntry("부산 지역 폭염 특보가 발표되어 야외 일정을 최소화했습니다.",
                        "Une alerte (canicule) est en vigueur à Busan : les activités en extérieur ont été réduites.")
                .containsEntry("매우 나쁨", "Très mauvais")
                .containsEntry("높음", "Élevé")
                .containsEntry("주의", "Prudence");
    }
}
