package com.example.demo.trip.service;

import com.example.demo.config.CurrentLanguage;
import com.example.demo.config.LocalizedMessages;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.transport.service.TransportNameTranslator;
import com.example.demo.trip.dto.TripPlanDto;
import com.example.demo.trip.dto.TripRecommendResponse;
import com.example.demo.trip.dto.TripRecommendResponse.RecommendedPlace;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 여행 일정 화면에 보이는 장소 이름·주소와 날씨 문구를 화면 언어로 옮긴다.
 *
 * <p>일정 추천의 장소 데이터는 국문·영문 두 벌이라, 일본어·중국어·불어로 만든 일정도 장소 이름이
 * 국문으로 보였다. 날씨 특보·대기질·자외선 단계는 어느 언어든 국문이었다.
 *
 * <p>응답의 국문 값은 바꾸지 않는다. 화면이 이름으로 대체 후보를 찾고, 재조정 요청에 그대로
 * 돌려보내고, 지도 검색어로도 쓰기 때문이다. 또 일정은 브라우저에 저장됐다가 다른 언어로 다시
 * 열리기도 해서, 값을 옮겨 버리면 되돌릴 수 없다. 대신 "국문 → 화면 언어" 표({@code displayText})와
 * 그 언어({@code displayLang})를 따로 실어 보내고, 화면은 보여 줄 때만 이 표를 본다.
 * 날씨 특보·대기질·자외선 단계도 같은 표에 넣는다.
 * <ul>
 *   <li>관광공사 번호가 있으면 관광 화면과 같은 번역 표(TourPlaceI18n)로 옮긴다.</li>
 *   <li>번호가 없는 이름(저장된 일정, 축제)은 번역기로 옮기고 기억한다.</li>
 * </ul>
 */
@Component
public class TripDisplayText {

    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Pattern HAZARD_WARNING = Pattern.compile("^부산 지역 (\\S+) 특보가 발표되어 야외 일정을 최소화했습니다\\.$");
    private static final String GENERAL_WARNING = "부산 지역 기상특보가 발표되어 야외 활동 전 최신 정보를 확인하세요.";
    private static final Map<String, String> HAZARDS = Map.of(
            "태풍", "typhoon", "호우", "heavyRain", "강풍", "wind",
            "폭염", "heat", "한파", "cold", "풍랑", "waves");
    /** 대기질·자외선·폭염·한파 단계(국문) → 문구 키 끝. */
    private static final Map<String, String> LEVELS = Map.ofEntries(
            Map.entry("좋음", "good"), Map.entry("보통", "normal"), Map.entry("나쁨", "bad"),
            Map.entry("매우 나쁨", "veryBad"), Map.entry("정보 없음", "none"), Map.entry("위험", "danger"),
            Map.entry("매우 높음", "veryHigh"), Map.entry("높음", "high"), Map.entry("낮음", "low"),
            Map.entry("관심", "interest"), Map.entry("주의", "caution"), Map.entry("경고", "warning"),
            Map.entry("경계", "alert"), Map.entry("심각", "severe"));

    private final TourPlaceI18n placeI18n;
    private final TransportNameTranslator names;
    private final LocalizedMessages messages;

    public TripDisplayText(TourPlaceI18n placeI18n, TransportNameTranslator names, LocalizedMessages messages) {
        this.placeI18n = placeI18n;
        this.names = names;
        this.messages = messages;
    }

    /** 추천·재조정·저장 일정 다시 보기 응답. 국문 화면이면 그대로 돌려준다. */
    public TripRecommendResponse apply(TripRecommendResponse resp) {
        String lang = CurrentLanguage.get();
        if (resp == null || CurrentLanguage.KO.equals(lang)) return resp;

        Map<String, String> text = new LinkedHashMap<>();
        Set<String> withoutId = new LinkedHashSet<>();
        for (List<RecommendedPlace> list : java.util.Arrays.asList(resp.getTourismList(), resp.getFoodList(), resp.getCafeList())) {
            if (list == null) continue;
            list.forEach(p -> collect(text, withoutId, lang, p.getName(), p.getContentId(), p.getAddress()));
        }
        if (resp.getSchedule() != null) {
            resp.getSchedule().forEach(day -> {
                if (day.getSlots() == null) return;
                day.getSlots().forEach(slot -> {
                    collect(text, withoutId, lang, slot.getPlaceName(), slot.getContentId(), slot.getAddress());
                    if (slot.getAlternatives() != null) {
                        slot.getAlternatives().forEach(a ->
                                collect(text, withoutId, lang, a.getPlaceName(), a.getContentId(), a.getAddress()));
                    }
                });
            });
        }
        if (resp.getFestivals() != null) {
            resp.getFestivals().forEach(f -> collect(text, withoutId, lang, f.getTitle(), null, null));
        }
        text.putAll(names.translateAll(withoutId, lang));

        putTranslated(text, resp.getWeatherWarning(), warning(resp.getWeatherWarning(), lang));
        if (resp.getAirQuality() != null) {
            String grade = resp.getAirQuality().getGrade();
            putTranslated(text, grade, level(grade, lang));
        }
        if (resp.getHealthRisk() != null) {
            var risk = resp.getHealthRisk();
            for (String value : new String[]{risk.getUvLevel(), risk.getHeatRisk(), risk.getColdRisk()}) {
                putTranslated(text, value, level(value, lang));
            }
        }
        resp.setDisplayText(text);
        resp.setDisplayLang(lang);
        return resp;
    }

    /** "근처 맛집/카페" 처럼 목록만 따로 받는 경우. 항목마다 보여 줄 이름·주소를 채운다. */
    public List<RecommendedPlace> apply(List<RecommendedPlace> places) {
        String lang = CurrentLanguage.get();
        if (places == null || CurrentLanguage.KO.equals(lang)) return places;
        Map<String, String> text = new LinkedHashMap<>();
        Set<String> withoutId = new LinkedHashSet<>();
        places.forEach(p -> collect(text, withoutId, lang, p.getName(), p.getContentId(), p.getAddress()));
        text.putAll(names.translateAll(withoutId, lang));
        places.forEach(p -> {
            p.setDisplayName(text.get(p.getName()));
            p.setDisplayAddress(p.getAddress() == null ? null : text.get(p.getAddress()));
        });
        return places;
    }

    /** 저장된 일정 상세(마이페이지, 내 일정). 장소 번호가 저장돼 있지 않아 이름은 번역기로 옮긴다. */
    public TripPlanDto.Detail apply(TripPlanDto.Detail detail) {
        String lang = CurrentLanguage.get();
        if (detail == null || CurrentLanguage.KO.equals(lang) || detail.getDays() == null) return detail;
        Set<String> placeNames = new LinkedHashSet<>();
        detail.getDays().forEach(day -> {
            if (day.getSlots() != null) day.getSlots().forEach(s -> {
                if (hasHangul(s.getPlaceName())) placeNames.add(s.getPlaceName());
            });
        });
        detail.setDisplayText(names.translateAll(placeNames, lang));
        return detail;
    }

    private static void putTranslated(Map<String, String> text, String korean, String translated) {
        if (korean != null && translated != null && !translated.equals(korean)) text.put(korean, translated);
    }

    private void collect(Map<String, String> text, Set<String> withoutId, String lang,
                         String name, String contentId, String address) {
        if (hasHangul(name) && !text.containsKey(name)) {
            String translated = hasText(contentId) ? placeI18n.translateTitle(lang, contentId, name) : null;
            if (translated != null && !translated.equals(name)) text.put(name, translated);
            else withoutId.add(name);
        }
        if (hasHangul(address) && hasText(contentId) && !text.containsKey(address)) {
            String translated = placeI18n.translateAddress(lang, contentId, address);
            if (translated != null && !translated.equals(address)) text.put(address, translated);
        }
    }

    /** 대기질·자외선·폭염·한파 단계. 모르는 국문 값은 번역기로. */
    String level(String value, String lang) {
        if (!hasHangul(value)) return value;
        String key = LEVELS.get(value.trim());
        if (key != null) return messages.get("trip.weather.level." + key);
        return names.translate(value, lang);
    }

    String warning(String value, String lang) {
        if (!hasHangul(value)) return value;
        if (GENERAL_WARNING.equals(value.trim())) return messages.get("trip.weather.warning.general");
        Matcher m = HAZARD_WARNING.matcher(value.trim());
        if (m.matches() && HAZARDS.containsKey(m.group(1))) {
            String hazard = messages.get("trip.weather.hazard." + HAZARDS.get(m.group(1)));
            return messages.get("trip.weather.warning.hazard", hazard);
        }
        return names.translate(value, lang);
    }

    private static boolean hasHangul(String s) {
        return s != null && HANGUL.matcher(s).find();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
