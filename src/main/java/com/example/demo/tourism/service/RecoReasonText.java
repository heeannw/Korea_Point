package com.example.demo.tourism.service;

import com.example.demo.community.service.PlaceLabelTranslator;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * "나를 위한 추천" 카드의 근거 태그와 폴백 문장을 화면 언어로 만든다.
 *
 * <p>예전에는 "관광지 선호", "해운대구", "기반 추천" 이 국문으로 박혀 있어서 일본어로 봐도
 * "관광지 선호 기반 추천" 이 보였다. 문구는 문구 파일(reco.*)에서, 구 이름은 커뮤니티
 * 꼬리표와 같은 이름표에서 가져온다. 소분류 이름은 후보 목록이 이미 그 언어라 그대로 쓴다.
 */
@Component
public class RecoReasonText {

    private final MessageSource messages;
    private final PlaceLabelTranslator places;

    public RecoReasonText(MessageSource messages, PlaceLabelTranslator places) {
        this.messages = messages;
        this.places = places;
    }

    /** "ja", "zh-CN", null → "ja", "zh", "ko". */
    public static String language(String lang) {
        if (lang == null || lang.isBlank()) return "ko";
        String l = lang.trim().toLowerCase(Locale.ROOT);
        int dash = l.indexOf('-');
        return dash > 0 ? l.substring(0, dash) : l;
    }

    /** "관광지 선호" */
    public String likes(String categorySlug, String lang) {
        String category = get("reco.category." + (categorySlug == null ? "tour" : categorySlug), lang);
        return get("reco.reason.likes", lang, category);
    }

    /** "해운대구" → "Haeundae-gu". 모르는 이름은 그대로. */
    public String area(String gu, String lang) {
        return places.translate(language(lang), gu, null);
    }

    /** "미술관 자주 봄" */
    public String oftenViewed(String detail, String lang) {
        return get("reco.reason.oftenViewed", lang, detail);
    }

    /** Gemini 를 못 쓸 때 카드에 넣는 문장. "관광지 선호 · 해운대구 기반 추천" */
    public String basedOn(List<String> reasons, String lang) {
        return get("reco.reason.basedOn", lang, String.join(" · ", reasons));
    }

    private String get(String code, String lang, Object... args) {
        return messages.getMessage(code, args, code, Locale.forLanguageTag(language(lang)));
    }
}
