package com.example.demo.transport.service;

import com.example.demo.community.service.PlaceLabelTranslator;
import com.example.demo.tourism.config.DeeplTranslator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransportNameTranslatorTest {

    private DeeplTranslator deepl;
    private PlaceLabelTranslator places;
    private TransportNameTranslator translator;

    @BeforeEach
    void setUp() {
        deepl = mock(DeeplTranslator.class);
        places = mock(PlaceLabelTranslator.class);
        when(places.translate(anyString(), anyString(), isNull())).thenAnswer(inv -> inv.getArgument(1));
        translator = new TransportNameTranslator(deepl, places, new ObjectMapper());
        translator.load();
    }

    @Test
    void 역_이름은_사전에서_찾고_번역기를_부르지_않는다() {
        // "서면" 만 보내면 번역기가 일본어·중국어에서 書面(서류)으로 옮겼다.
        assertThat(translator.translate("서면", "ja")).isEqualTo("西面");
        assertThat(translator.translate("서면역", "zh")).isEqualTo("西面站");
        assertThat(translator.translate("서면역", "en")).isEqualTo("Seomyeon Station");
        assertThat(translator.translate("서면역", "fr")).isEqualTo("Station Seomyeon");
        verify(deepl, never()).translateAll(anyList(), any());
    }

    @Test
    void 이름에_역이_들어간_역은_두_번_붙이지_않는다() {
        assertThat(translator.translate("부산역", "ja")).isEqualTo("釜山駅");
        assertThat(translator.translate("부산역", "en")).isEqualTo("Busan Station");
        assertThat(translator.translate("부산역", "fr")).isEqualTo("Gare de Busan");
    }

    @Test
    void 국문이거나_한글이_없으면_그대로다() {
        assertThat(translator.translate("서면", "ko")).isEqualTo("서면");
        assertThat(translator.translate("1001", "ja")).isEqualTo("1001");
        assertThat(translator.translateAll(List.of("서면"), null)).isEmpty();
    }

    @Test
    void 나머지는_한_번에_보내고_기억한다() {
        when(deepl.translateAll(eq(List.of("하단교차로", "부산 1호선")), eq("fr")))
                .thenReturn(List.of("Carrefour de Hadan", "Ligne 1 de Busan"));

        Map<String, String> out = translator.translateAll(List.of("서면역", "하단교차로", "부산 1호선", "하단교차로"), "fr");
        assertThat(out).containsEntry("서면역", "Station Seomyeon")
                .containsEntry("하단교차로", "Carrefour de Hadan")
                .containsEntry("부산 1호선", "Ligne 1 de Busan");

        assertThat(translator.translate("하단교차로", "fr")).isEqualTo("Carrefour de Hadan");
        verify(deepl, times(1)).translateAll(anyList(), any());
    }

    @Test
    void 번역에_실패하면_국문을_두고_다음에_다시_시도한다() {
        when(deepl.translateAll(anyList(), eq("zh"))).thenReturn(null);
        assertThat(translator.translate("하단교차로", "zh")).isEqualTo("하단교차로");
        translator.translate("하단교차로", "zh");
        verify(deepl, times(2)).translateAll(anyList(), eq("zh"));
    }

    @Test
    void 장소_꼬리표_표에_있으면_그_표기를_쓴다() {
        when(places.translate(eq("zh"), eq("광안리해수욕장"), isNull())).thenReturn("广安里海水浴场");
        assertThat(translator.translate("광안리해수욕장", "zh-CN")).isEqualTo("广安里海水浴场");
        verify(deepl, never()).translateAll(anyList(), any());
    }
}
