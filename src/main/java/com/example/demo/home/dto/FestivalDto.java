package com.example.demo.home.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 홈 화면의 축제 카드 하나에 들어갈 데이터.
 *
 * <p>관광공사 응답을 화면에서 바로 쓸 수 있는 형태로 바꿔 담는다.
 * 날짜 표기(8.28 ~ 8.29)나 진행중 여부처럼 화면에서 계산하기 번거로운 것들은
 * 미리 만들어 두어서, 템플릿에서는 꺼내 쓰기만 하면 되게 했다.
 */
@Getter
@Builder
public class FestivalDto {

    /** 관광공사 콘텐츠 ID — 나중에 상세 페이지로 연결할 때 쓴다 */
    private final String contentId;

    private final String title;
    private final String address;

    /** 대표 이미지 (https 로 맞춰둔 주소) */
    private final String imageUrl;

    /**
     * 썸네일 (약 150x100). 카드 뒤에 흐리게 까는 용도라 화질이 필요 없고,
     * 배경 이미지는 lazy 로딩이 안 되므로 작은 파일을 써야 첫 로딩이 늦지 않는다.
     */
    private final String thumbnailUrl;

    /** 행사 기간 원본 (yyyyMMdd) — 정렬에 쓴다 */
    private final String startDate;
    private final String endDate;

    /** 화면 표기용 기간 — 예: "8.28 ~ 8.29" */
    private final String period;

    /** 오늘 기준 진행중인지 (아니면 아직 시작 안 한 행사) */
    private final boolean ongoing;

    /**
     * 시작까지 남은 날. 진행중이면 0.
     *
     * <p>예전에는 여기에 "진행중" / "3일 뒤" 같은 완성된 문구가 국문·영문
     * 두 벌로 들어 있었다. 그러면 언어가 늘 때마다 자바를 고쳐야 하고,
     * 실제로 일본어·중국어·불어 화면에 한국어 배지가 그대로 나왔다.
     * 숫자만 넘기고 문구는 messages 파일에서 고른다.
     */
    private final long daysLeft;

    /**
     * 이미지를 잘라내거나 필터를 씌워도 되는지.
     *
     * <p>관광공사 이미지 중 상당수가 "출처표시 + 변경금지"(Type3) 라서
     * 정사각형으로 크롭하는 흔한 카드 디자인이 저작권 위반이 된다.
     * 이 값이 false 면 원본 비율 그대로 보여줘야 한다.
     */
    private final boolean imageModifiable;
}
