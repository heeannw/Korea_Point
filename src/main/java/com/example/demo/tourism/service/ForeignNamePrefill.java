package com.example.demo.tourism.service;

import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.tourism.dto.TourPlaceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 외국어 이름이 없는 장소를 미리 찾아 번역 대기열에 올린다.
 *
 * <p>관광공사 외국어 목록에 아예 없는 장소가 많다. 부산 2,218곳 기준으로 영어·일본어·중국어는
 * 1,200곳 안팎, 프랑스어는 2,144곳이 빠져 있다. 이런 장소는 {@link TourPlaceI18n} 이 번역으로
 * 메우는데, 여태는 <b>누군가 그 화면을 연 뒤에야</b> 대기열에 올랐다. 그래서 처음 연 사람은
 * 외국어 화면에서 한국어 이름을 보고, 잠시 뒤 다시 열어야 번역된 이름이 나왔다.
 *
 * <p>여기서 부산 장소를 한 번 훑어 빠진 것을 미리 올려 둔다. 채우는 일과 저장은
 * {@link TourPlaceTranslationFiller} 가 하던 그대로다. 한 번 채우면 표에 남아 다시 번역하지 않는다.
 *
 * <p>화면을 그리는 길목이 아니라 정기 작업으로 돈다. 앱이 뜨는 것을 늦추지 않고, 번역기가
 * 느리거나 막힌 날에도 화면은 국문 이름으로 멀쩡히 나온다.
 */
@Service
@RequiredArgsConstructor
public class ForeignNamePrefill {

    private static final Logger log = LoggerFactory.getLogger(ForeignNamePrefill.class);

    /** 사이트가 지원하는 외국어. 국문은 원문이라 뺀다. */
    private static final List<String> LANGUAGES = List.of("en", "ja", "zh", "fr");

    /** 하루 한 번. 관광공사 목록에 장소가 새로 생기면 다음 날 잡힌다. */
    private static final long REFRESH_MS = 24 * 60 * 60_000L;

    private final TourApiClient tourApiClient;
    private final TourPlaceI18n placeI18n;

    /**
     * 배포 서버에서만 켠다({@code application-local.properties} 에 한 줄).
     *
     * <p>팀원 각자의 컴퓨터에서 켜져 있으면 같은 번역을 사람 수만큼 반복해서
     * 번역기 한도를 며칠 만에 다 쓴다. 한 번 채운 이름은 서버 DB 에 남고, 개발할 때는
     * 외국어 이름이 한국어로 보일 뿐 화면은 멀쩡히 돈다.
     */
    @Value("${translation.fill.enabled:false}")
    private boolean enabled;

    /**
     * 부산 장소를 언어별로 훑는다. 공사 표에 이름이 있으면 그것을 쓰고, 없으면 대기열에 오른다
     * (둘 다 {@link TourPlaceI18n} 이 알아서 한다).
     *
     * <p>앱이 뜨고 1분 뒤에 시작한다. 관광 목록과 번역 오버레이가 먼저 자리를 잡아야
     * 빠진 것을 제대로 가려낼 수 있다.
     */
    @Scheduled(initialDelay = 60_000, fixedRate = REFRESH_MS)
    public void prefill() {
        if (!enabled) return;
        try {
            List<TourPlaceDto> places = tourApiClient.findBusanPlaces("ko");
            if (places.isEmpty()) return;
            for (String lang : LANGUAGES) {
                for (TourPlaceDto p : places) {
                    placeI18n.translateTitle(lang, p.getId(), p.getTitle());
                    placeI18n.translateAddress(lang, p.getId(), p.getAddr());
                }
            }
            log.info("외국어 이름 미리 채우기: 부산 {}곳 × {}개 언어 확인", places.size(), LANGUAGES.size());
        } catch (Exception e) {
            // 실패해도 화면은 국문 이름으로 나온다. 다음 차례에 다시 해 본다.
            log.warn("외국어 이름 미리 채우기 실패: {}", e.getMessage());
        }
    }
}
