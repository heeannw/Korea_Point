package com.example.demo.tourism.service;

import com.example.demo.tourism.config.DeeplTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TourDetailValueTranslatorTest {

    private DeeplTranslator deepl;
    private TourDetailValueTranslator translator;

    @BeforeEach
    void setUp() {
        deepl = mock(DeeplTranslator.class);
        translator = new TourDetailValueTranslator(deepl, new ObjectMapper());
        translator.load();
    }

    private static Map<String, String> details(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void 국문이면_그대로다() {
        Map<String, String> in = details("restdate", "연중무휴");
        assertThat(translator.translate(in, "ko")).isSameAs(in);
        verify(deepl, never()).translateAll(anyList(), any());
    }

    @Test
    void 짧은_값은_이름표로_옮기고_번역기를_부르지_않는다() {
        Map<String, String> out = translator.translate(details("parking", "가능", "restdate", " 연중무휴 "), "fr");
        assertThat(out).containsEntry("parking", "Disponible")
                .containsEntry("restdate", "Ouvert toute l&#39;année");
        verify(deepl, never()).translateAll(anyList(), any());
    }

    @Test
    void 한글이_없는_값은_건드리지_않는다() {
        Map<String, String> out = translator.translate(details("usetime", "09:00~18:00", "infocenter", "051-123-4567"), "en");
        assertThat(out).containsEntry("usetime", "09:00~18:00").containsEntry("infocenter", "051-123-4567");
        verify(deepl, never()).translateAll(anyList(), any());
    }

    @Test
    void 나머지는_태그를_줄바꿈으로_바꿔_한_번에_보낸다() {
        when(deepl.translateAll(eq(List.of("매주 월요일\n※ 홈페이지 공지", "09:00~20:00 (입장 마감 19:00)")), eq("en")))
                .thenReturn(List.of("Every Monday\n※ See website", "9:00 a.m.–8:00 p.m. (Last entry 7 p.m.)"));

        Map<String, String> out = translator.translate(details(
                "restdate", "매주 월요일<br>※ 홈페이지 공지",
                "usetime", "09:00~20:00 (입장 마감 19:00)"), "en");

        assertThat(out.get("restdate")).isEqualTo("Every Monday\n※ See website");
        assertThat(out.get("usetime")).isEqualTo("9:00 a.m.–8:00 p.m. (Last entry 7 p.m.)");
        verify(deepl, times(1)).translateAll(anyList(), eq("en"));
    }

    @Test
    void 메뉴는_국문을_괄호로_덧붙인다() {
        when(deepl.translateAll(eq(List.of("회비빔밥")), eq("ja"))).thenReturn(List.of("刺身ビビンバ"));
        Map<String, String> out = translator.translate(details("firstmenu", "회비빔밥"), "ja");
        assertThat(out.get("firstmenu")).isEqualTo("刺身ビビンバ (회비빔밥)");
    }

    @Test
    void 번역에_실패하면_국문을_두고_기억하지_않는다() {
        when(deepl.translateAll(anyList(), eq("zh"))).thenReturn(null);
        Map<String, String> in = details("usetime", "주중 10:30~21:00");
        assertThat(translator.translate(in, "zh")).containsEntry("usetime", "주중 10:30~21:00");
        translator.translate(in, "zh");
        verify(deepl, times(2)).translateAll(anyList(), eq("zh"));
    }

    @Test
    void 한_번_옮긴_값은_다시_보내지_않는다() {
        when(deepl.translateAll(eq(List.of("매장별로 상이함")), eq("en"))).thenReturn(List.of("Varies by store"));
        translator.translate(details("restdate", "매장별로 상이함"), "en");
        Map<String, String> again = translator.translate(details("opentime", "매장별로 상이함"), "en");
        assertThat(again).containsEntry("opentime", "Varies by store");
        verify(deepl, times(1)).translateAll(anyList(), eq("en"));
    }

    @Test
    void 주소_뒤에_붙은_한글은_띄어서_보낸다() {
        assertThat(TourDetailValueTranslator.plain("홈페이지 http://youth.busan.go.kr전화 문의"))
                .isEqualTo("홈페이지 http://youth.busan.go.kr 전화 문의");
        assertThat(TourDetailValueTranslator.plain("09:00~20:00<br>※ 점포별 &amp; 상이"))
                .isEqualTo("09:00~20:00\n※ 점포별 & 상이");
    }

    @Test
    void 번역문의_특수문자는_이스케이프한다() {
        when(deepl.translateAll(anyList(), eq("en"))).thenReturn(List.of("Tools & <lighting>"));
        Map<String, String> out = translator.translate(details("saleitem", "공구 / 조명"), "en");
        assertThat(out.get("saleitem")).isEqualTo("Tools &amp; &lt;lighting&gt;");
    }
}
