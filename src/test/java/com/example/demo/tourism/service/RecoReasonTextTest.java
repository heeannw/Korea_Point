package com.example.demo.tourism.service;

import com.example.demo.community.service.PlaceLabelTranslator;
import com.example.demo.tourism.ai.GeminiRecoReasonService;
import com.example.demo.tourism.dto.PersonalRecoDto;
import com.example.demo.tourism.dto.UserTasteProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "나를 위한 추천" 근거 문구가 화면 언어로 나오는지 본다. 국문은 예전 문구 그대로여야 한다. */
class RecoReasonTextTest {

    private RecoReasonText text;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);

        PlaceLabelTranslator places = mock(PlaceLabelTranslator.class);
        when(places.translate(eq("ko"), eq("수영구"), isNull())).thenReturn("수영구");
        when(places.translate(eq("zh"), eq("수영구"), isNull())).thenReturn("水营区");
        text = new RecoReasonText(source, places);
    }

    @Test
    void 국문은_예전_문구와_같다() {
        List<String> tags = List.of(text.likes("culture", "ko"), text.area("수영구", "ko"), text.oftenViewed("미술관", "ko"));
        assertThat(tags).containsExactly("문화시설 선호", "수영구", "미술관 자주 봄");
        assertThat(text.basedOn(tags, "ko")).isEqualTo("문화시설 선호 · 수영구 · 미술관 자주 봄 기반 추천");
    }

    @Test
    void 외국어는_그_언어로_나온다() {
        assertThat(text.basedOn(List.of(text.likes("attraction", "ja")), "ja")).isEqualTo("おすすめ理由：観光地好き");
        assertThat(text.basedOn(List.of(text.likes(null, "en")), "en")).isEqualTo("Based on: Likes Sightseeing");
        assertThat(text.likes("leisure", "fr")).isEqualTo("Intérêt : Loisirs et activités");
    }

    @Test
    void 지역표기가_붙은_언어도_받는다() {
        assertThat(RecoReasonText.language("zh-CN")).isEqualTo("zh");
        assertThat(RecoReasonText.language(null)).isEqualTo("ko");
        assertThat(text.area("수영구", "zh-CN")).isEqualTo("水营区");
        assertThat(text.oftenViewed("博物馆", "zh-CN")).isEqualTo("常看博物馆");
    }

    @Test
    void 제미나이를_못_쓰면_화면_언어로_된_폴백_문장이_붙는다() {
        GeminiRecoReasonService service = new GeminiRecoReasonService(text);   // 키 없음
        UserTasteProfile profile = new UserTasteProfile(Map.of("attraction", 1.0), Map.of(), Map.of(),
                Set.of(), null, null, false);
        PersonalRecoDto reco = new PersonalRecoDto("1", "x", "12", "attraction", null, 0.5,
                List.of(text.likes("attraction", "ja")), null);

        assertThat(service.explain(profile, List.of(reco), "ja").get(0).genReason())
                .isEqualTo("おすすめ理由：観光地好き");
    }
}
