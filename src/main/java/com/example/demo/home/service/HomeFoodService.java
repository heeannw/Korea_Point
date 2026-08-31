package com.example.demo.home.service;

import com.example.demo.common.tourapi.TourApiClient;
import com.example.demo.common.tourapi.TourApiLang;
import com.example.demo.common.tourapi.TourApiQuery;
import com.example.demo.common.tourapi.TourItem;
import com.example.demo.home.dto.PlaceCardDto;
import com.example.demo.tourism.config.TourCategoryNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 홈 "부산 맛집"에 띄울 음식점·카페를 가져온다.
 *
 * <p>관광공사는 카페를 음식점 유형(39/82) 안에 담아 준다. 관광 탭이 한 화면에서
 * 탭으로 나누고 있으므로 홈도 한 섹션으로 묶되, 카드가 한쪽으로 쏠리지 않도록
 * 음식점과 카페를 번갈아 담는다. 한쪽이 모자라면 다른 쪽으로 채운다
 * (영문 서비스에는 카페가 한 건도 없다).
 */
@Service
@RequiredArgsConstructor
public class HomeFoodService {

    /** 분류체계에서 카페·찻집·주점이 묶이는 중분류 */
    private static final String CAFE_GROUP = "FD05";

    /** 한식 중분류 — 소분류가 음식 종류가 아니라 인증 구분이라 따로 다룬다 */
    private static final String KOREAN_FOOD_GROUP = "FD01";

    /** 절반씩 뽑으려면 후보를 넉넉히 받아야 한다 */
    private static final int FETCH_SIZE = 100;

    private final TourApiClient tourApiClient;
    private final TourCategoryNames categoryNames;

    public List<PlaceCardDto> getFoodPlaces(String lang, int limit) {
        List<TourItem> items = tourApiClient.areaBasedList(
                TourApiQuery.of()
                        .lang(lang)
                        .contentTypeId(TourApiLang.contentTypeId(lang, TourApiLang.RESTAURANT))
                        .numOfRows(FETCH_SIZE)
                        .build()
                        .withImageOnly());

        List<TourItem> cafes = new ArrayList<>();
        List<TourItem> restaurants = new ArrayList<>();
        for (TourItem item : items) {
            if (!item.hasImage()) {
                continue;
            }
            (isCafe(item) ? cafes : restaurants).add(item);
        }

        return interleave(restaurants, cafes, limit).stream()
                .map(item -> toDto(item, lang))
                .toList();
    }

    /**
     * 두 목록을 번갈아 담는다. 한쪽이 먼저 떨어지면 남은 쪽으로 마저 채운다.
     * 카페가 없는 언어에서도 카드 수가 줄지 않게 하려는 것이다.
     */
    private List<TourItem> interleave(List<TourItem> a, List<TourItem> b, int limit) {
        List<TourItem> result = new ArrayList<>(limit);
        int i = 0;
        while (result.size() < limit && (i < a.size() || i < b.size())) {
            if (i < a.size() && result.size() < limit) result.add(a.get(i));
            if (i < b.size() && result.size() < limit) result.add(b.get(i));
            i++;
        }
        return result;
    }

    private boolean isCafe(TourItem item) {
        return CAFE_GROUP.equals(item.getLclsSystm2());
    }

    private PlaceCardDto toDto(TourItem item, String lang) {
        return PlaceCardDto.builder()
                .contentId(item.getContentId())
                .title(item.getTitle())
                .address(item.getAddress())
                .category(categoryLabel(item, lang))
                .imageUrl(toHttps(item.getImageUrl()))
                .imageModifiable(item.isImageModifiable())
                .build();
    }

    /**
     * 카드에 붙일 분류 이름.
     *
     * <p>국문은 관광 탭이 쓰는 분류표를 그대로 재사용한다(한식·일식·카페 …).
     * 그 표가 한국어뿐이라 다른 언어에서는 카페인지 아닌지만 구분해 준다.
     */
    private String categoryLabel(TourItem item, String lang) {
        if (!TourApiLang.KO.equals(lang)) {
            return isCafe(item) ? "Cafe" : "Restaurant";
        }

        String small = item.getLclsSystm3();
        String medium = item.getLclsSystm2();

        // 한식의 소분류는 '관광식당·모범음식점' 같은 인증 구분이라 음식 종류가 아니다.
        // 그대로 쓰면 한식이 전부 '관광식당'으로 보인다(관광 탭도 같은 이유로 중분류를 쓴다).
        if (medium != null && medium.startsWith(KOREAN_FOOD_GROUP)) {
            small = null;
        }
        return categoryNames.resolve(small, medium, item.getLclsSystm1());
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
