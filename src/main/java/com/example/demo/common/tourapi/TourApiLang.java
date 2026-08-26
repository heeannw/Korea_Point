package com.example.demo.common.tourapi;

import java.util.Map;

/**
 * 한국관광공사 다국어 서비스 매핑.
 *
 * <p>언어별로 <b>서비스가 완전히 분리</b>되어 있고(KorService2 / JpnService2 …),
 * 같은 "관광지"라도 <b>관광타입 코드가 다르다</b>.
 * 국문은 12·14·15…, 다국어는 76·78·85… 를 쓴다.
 * 국문 코드를 다국어 서비스에 그대로 넣으면 결과가 비어서 돌아온다.
 *
 * <p>또한 다국어 서비스에는 여행코스(25)가 없고, 대신 교통(77)이 있다.
 */
public final class TourApiLang {

    public static final String KO = "ko";
    public static final String EN = "en";
    public static final String JA = "ja";
    public static final String ZH_CN = "zh-CN";
    public static final String ZH_TW = "zh-TW";
    public static final String DE = "de";
    public static final String FR = "fr";
    public static final String ES = "es";
    public static final String RU = "ru";

    /** 언어 → 서비스 ID */
    private static final Map<String, String> SERVICE_IDS = Map.of(
            KO,    "KorService2",
            EN,    "EngService2",
            JA,    "JpnService2",
            ZH_CN, "ChsService2",
            ZH_TW, "ChtService2",
            DE,    "GerService2",
            FR,    "FreService2",
            ES,    "SpnService2",
            RU,    "RusService2"
    );

    // ── 관광타입 코드 ────────────────────────────────────────
    public static final String TOURIST_SPOT = "tourist_spot";
    public static final String CULTURE      = "culture";
    public static final String FESTIVAL     = "festival";
    public static final String COURSE       = "course";     // 국문 전용
    public static final String LEPORTS      = "leports";
    public static final String LODGING      = "lodging";
    public static final String SHOPPING     = "shopping";
    public static final String RESTAURANT   = "restaurant";
    public static final String TRANSPORT    = "transport";  // 다국어 전용

    /** 국문 서비스 관광타입 코드 */
    private static final Map<String, String> KO_TYPES = Map.of(
            TOURIST_SPOT, "12",
            CULTURE,      "14",
            FESTIVAL,     "15",
            COURSE,       "25",
            LEPORTS,      "28",
            LODGING,      "32",
            SHOPPING,     "38",
            RESTAURANT,   "39"
    );

    /** 다국어 서비스 관광타입 코드 (9개 언어 공통) */
    private static final Map<String, String> MULTI_TYPES = Map.of(
            LEPORTS,      "75",
            TOURIST_SPOT, "76",
            TRANSPORT,    "77",
            CULTURE,      "78",
            SHOPPING,     "79",
            LODGING,      "80",
            RESTAURANT,   "82",
            FESTIVAL,     "85"
    );

    private TourApiLang() {
    }

    /** 지원하는 언어인지 */
    public static boolean isSupported(String lang) {
        return SERVICE_IDS.containsKey(normalize(lang));
    }

    /** 언어 코드 → 서비스 ID (모르는 언어는 국문으로) */
    public static String serviceId(String lang) {
        return SERVICE_IDS.getOrDefault(normalize(lang), SERVICE_IDS.get(KO));
    }

    /**
     * 관광타입 이름 → 해당 언어의 코드.
     *
     * @return 그 언어에 없는 타입이면 null (예: 다국어의 여행코스, 국문의 교통)
     */
    public static String contentTypeId(String lang, String type) {
        return KO.equals(normalize(lang))
                ? KO_TYPES.get(type)
                : MULTI_TYPES.get(type);
    }

    /** 브라우저 로케일 등에서 넘어온 값을 내부 코드로 정리 */
    private static String normalize(String lang) {
        if (lang == null || lang.isBlank()) {
            return KO;
        }
        String v = lang.trim();
        if (SERVICE_IDS.containsKey(v)) {
            return v;
        }
        // "kr", "KO", "en-US", "ja_JP" 같은 표기 흡수
        String lower = v.toLowerCase().replace('_', '-');
        if (lower.startsWith("kr") || lower.startsWith("ko")) return KO;
        if (lower.startsWith("en")) return EN;
        if (lower.startsWith("ja") || lower.startsWith("jp")) return JA;
        if (lower.startsWith("zh-tw") || lower.startsWith("zh-hant")) return ZH_TW;
        if (lower.startsWith("zh")) return ZH_CN;
        if (lower.startsWith("de")) return DE;
        if (lower.startsWith("fr")) return FR;
        if (lower.startsWith("es")) return ES;
        if (lower.startsWith("ru")) return RU;
        return KO;
    }
}
