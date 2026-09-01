package com.example.demo.home.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 홈 "가볼 만한 곳" 카드 하나에 들어갈 데이터.
 *
 * <p>별점·리뷰 수 같은 항목은 담지 않는다. 관광공사 API에 없는 값이라
 * 화면에 띄우려면 지어내야 하기 때문이다. 우리가 실제로 가진 것만 담는다.
 */
@Getter
@Builder
public class PlaceCardDto {

    /** 관광공사 콘텐츠 ID — 관광 페이지로 넘길 때 쓴다 */
    private final String contentId;

    private final String title;

    /** 주소 (예: 부산광역시 해운대구 …) */
    private final String address;

    /** 분류 이름 (예: 한식, 카페). 없으면 null — 카드에서 배지를 숨긴다 */
    private final String category;

    /** 대표 이미지 (https 로 맞춰둔 주소) */
    private final String imageUrl;

    /**
     * 썸네일 (약 150x100). 카드 뒤에 흐리게 까는 용도라 화질이 필요 없고,
     * 배경 이미지는 lazy 로딩이 안 되므로 작은 파일을 써야 첫 로딩이 늦지 않는다.
     */
    private final String thumbnailUrl;

    /**
     * 이미지를 잘라내도 되는지.
     * 관광공사 이미지의 상당수가 "변경금지"라 크롭이 위반이 된다.
     */
    private final boolean imageModifiable;
}
