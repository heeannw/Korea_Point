package com.example.demo.home.service;

import com.example.demo.common.tourapi.TourApiClient;
import com.example.demo.common.tourapi.TourApiLang;
import com.example.demo.common.tourapi.TourApiQuery;
import com.example.demo.common.tourapi.TourItem;
import com.example.demo.home.dto.PlaceCardDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 홈 "가볼 만한 곳"에 띄울 부산 관광지를 가져온다.
 *
 * <p>관광공사 API에는 인기순 정렬이 없다. 그래서 "인기"라고 부르지 않고
 * 추천으로 둔다. 정렬은 수정일순(대표이미지 필수)이라 관광공사가 최근에
 * 손본 항목이 앞에 오고, 그만큼 정보가 최신일 가능성이 높다.
 *
 * <p>나중에 자체 인기 지표(저장 횟수 등)가 쌓이면 이 서비스의 정렬만
 * 바꿔 끼우면 된다. 화면은 그대로 두고 데이터 출처만 교체하는 구조다.
 */
@Service
@RequiredArgsConstructor
public class HomePlaceService {

    private final TourApiClient tourApiClient;

    public List<PlaceCardDto> getRecommendedPlaces(String lang, int limit) {
        List<TourItem> items = tourApiClient.areaBasedList(
                TourApiQuery.of()
                        .lang(lang)
                        // 관광타입 코드는 언어마다 다르다 (국문 12, 다국어 76)
                        .contentTypeId(TourApiLang.contentTypeId(lang, TourApiLang.TOURIST_SPOT))
                        .numOfRows(limit * 2)   // 사진 없는 항목이 섞여 와도 채울 수 있게
                        .build()
                        .withImageOnly());

        return items.stream()
                .filter(TourItem::hasImage)
                .map(this::toDto)
                .limit(limit)
                .toList();
    }

    /**
     * 히어로 배경으로 쓸 부산 관광지 사진.
     *
     * <p>히어로는 화면을 꽉 채우는 배경이라 이미지가 잘린다.
     * 관광공사 사진의 상당수가 "변경금지"(Type3)이고 크롭이 그 위반에 해당하므로,
     * <b>변경이 허용된 사진만</b> 골라 쓴다.
     */
    public List<PlaceCardDto> getHeroImages(String lang, int limit) {
        List<TourItem> items = tourApiClient.areaBasedList(
                TourApiQuery.of()
                        .lang(lang)
                        .contentTypeId(TourApiLang.contentTypeId(lang, TourApiLang.TOURIST_SPOT))
                        .numOfRows(100)      // 변경 허용 사진이 3건 중 1건꼴이라 넉넉히 받는다
                        .build()
                        .withImageOnly());

        return items.stream()
                .filter(TourItem::hasImage)
                .filter(TourItem::isImageModifiable)
                .map(this::toDto)
                .limit(limit)
                .toList();
    }

    private PlaceCardDto toDto(TourItem item) {
        return PlaceCardDto.builder()
                .contentId(item.getContentId())
                .title(item.getTitle())
                .address(item.getAddress())
                .imageUrl(toHttps(item.getImageUrl()))
                .imageModifiable(item.isImageModifiable())
                .build();
    }

    /** 관광공사 이미지 주소는 http/https가 섞여 온다. https 사이트에서 막히지 않게 맞춘다. */
    private String toHttps(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.startsWith("http://")
                ? "https://" + url.substring("http://".length())
                : url;
    }
}
