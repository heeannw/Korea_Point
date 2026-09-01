package com.example.demo.home.service;

import com.example.demo.common.tourapi.TourApiClient;
import com.example.demo.common.tourapi.TourApiQuery;
import com.example.demo.common.tourapi.TourItem;
import com.example.demo.home.dto.FestivalDto;
import com.example.demo.common.tourapi.TourApiLang;
import com.example.demo.tourism.config.TourPlaceI18n;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * 홈 화면에 띄울 부산 축제를 가져온다.
 *
 * <p>축제를 홈의 첫 섹션으로 고른 이유는 관광공사 데이터 중 축제가 가장 상태가 좋기 때문이다.
 * 대표이미지 보유율이 99%로 카드가 비지 않고, 무엇보다 <b>행사 기간이 있는 유일한 데이터</b>라
 * "지금 이 순간에만 맞는 정보"를 보여줄 수 있다. 관광지·음식점은 1년 내내 같은 내용이다.
 *
 * <p>호출과 캐싱은 {@link TourApiClient} 가 맡는다. 여기서는 화면에 맞게 고르고 정렬만 한다.
 */
@Service
@RequiredArgsConstructor
public class HomeFestivalService {

    /** 정렬·거르기를 하려면 화면에 쓸 개수보다 넉넉히 받아와야 한다 */
    private static final int FETCH_SIZE = 30;

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    /**
     * 진행중인 행사를 먼저, 그 안에서는 곧 끝나는 순.
     * 그 다음이 예정 행사이고 시작이 임박한 순이다.
     *
     * <p>여행자 입장에서 "오늘 갈 수 있는 것"이 먼저고, 그중에서도 곧 끝나는 게 급하다.
     */
    private static final Comparator<FestivalDto> DISPLAY_ORDER =
            Comparator.comparing(FestivalDto::isOngoing, Comparator.reverseOrder())
                    .thenComparing((FestivalDto f) -> f.isOngoing() ? f.getEndDate() : f.getStartDate());

    private final TourApiClient tourApiClient;
    private final TourPlaceI18n placeI18n;

    /**
     * 지금 열리고 있거나 곧 열리는 부산 축제.
     *
     * @param lang  "ko" / "en" — 홈이 국문·영문 두 벌이라 언어별로 따로 부른다.
     *              관광공사는 언어마다 서비스가 분리되어 있어 영문은 영문 제목이 온다.
     * @param limit 화면에 띄울 개수
     * @return 데이터가 모자라면 요청한 개수보다 적게 올 수 있다. 실패하면 빈 목록.
     */
    public List<FestivalDto> getUpcomingFestivals(String lang, int limit) {
        // eventStartDate 를 비워두면 TourApiClient 가 오늘로 채운다.
        // 오늘을 기준으로 주면 아직 안 끝난 행사(진행중 + 예정)가 함께 온다.
        List<TourItem> items = tourApiClient.searchFestival(
                TourApiQuery.of()
                        .lang(TourApiLang.KO)
                        .numOfRows(FETCH_SIZE)
                        .build());

        String today = LocalDate.now().format(YMD);

        return items.stream()
                // 홈은 카드형이라 사진 없는 항목은 빈 칸으로 보인다. 아예 제외한다.
                .filter(TourItem::hasImage)
                .filter(this::hasPeriod)
                // 이미 끝난 행사가 섞여 오는 경우가 있어 한 번 더 거른다.
                .filter(it -> it.getEventEndDate().compareTo(today) >= 0)
                .map(it -> toDto(it, today, lang))
                .sorted(DISPLAY_ORDER)
                .limit(limit)
                .toList();
    }

    private FestivalDto toDto(TourItem item, String today, String lang) {
        String start = item.getEventStartDate();
        String end = item.getEventEndDate();
        boolean ongoing = start.compareTo(today) <= 0;
        long daysLeft = ongoing ? 0 : daysBetween(today, start);

        return FestivalDto.builder()
                .contentId(item.getContentId())
                .title(placeI18n.translateTitle(lang, item.getContentId(), item.getTitle()))
                .address(placeI18n.translateAddress(lang, item.getContentId(), item.getAddress()))
                .imageUrl(toHttps(item.getImageUrl()))
                .thumbnailUrl(toHttps(item.getThumbnailUrl()))
                .startDate(start)
                .endDate(end)
                .period(formatPeriod(start, end))
                .ongoing(ongoing)
                .badge(ongoing ? "진행중" : daysLeft + "일 뒤")
                .badgeEn(ongoing ? "Now on" : "in " + daysLeft + " days")
                .imageModifiable(item.isImageModifiable())
                .build();
    }

    /**
     * 대표이미지 주소를 https 로 맞춘다.
     *
     * <p>관광공사가 주는 이미지 주소는 http 와 https 가 절반씩 섞여 있다.
     * 우리 사이트는 https 로 서비스하므로 http 이미지는 브라우저가 막을 수 있다.
     * 같은 주소를 https 로 불러도 정상 응답하므로 앞부분만 바꿔 쓴다.
     */
    private String toHttps(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.startsWith("http://")
                ? "https://" + url.substring("http://".length())
                : url;
    }

    /** "20260828", "20260829" → "8.28 ~ 8.29" (같은 날이면 "8.28") */
    private String formatPeriod(String start, String end) {
        String s = shortDate(start);
        String e = shortDate(end);
        return s.equals(e) ? s : s + " ~ " + e;
    }

    /** "20260828" → "8.28" */
    private String shortDate(String ymd) {
        int month = Integer.parseInt(ymd.substring(4, 6));
        int day = Integer.parseInt(ymd.substring(6, 8));
        return month + "." + day;
    }

    private long daysBetween(String from, String to) {
        return ChronoUnit.DAYS.between(LocalDate.parse(from, YMD), LocalDate.parse(to, YMD));
    }

    /** 기간이 비어 있으면 카드에 쓸 수 없다 */
    private boolean hasPeriod(TourItem item) {
        return isYmd(item.getEventStartDate()) && isYmd(item.getEventEndDate());
    }

    private boolean isYmd(String v) {
        return v != null && v.length() == 8 && v.chars().allMatch(Character::isDigit);
    }
}
