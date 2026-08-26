package com.example.demo.common.tourapi;

import lombok.Builder;
import lombok.Getter;

/**
 * 한국관광공사 목록 조회 결과 1건.
 *
 * areaBasedList2 / searchFestival2 / searchKeyword2 / locationBasedList2 는
 * 응답 필드가 동일하므로 이 DTO 하나로 모두 담는다.
 * (일부 필드는 오퍼레이션에 따라 비어 있을 수 있다)
 */
@Getter
@Builder
public class TourItem {

    /** 콘텐츠 ID — 상세 조회(detailCommon2 등)의 키 */
    private final String contentId;

    /** 관광타입 ID — 국문 12/14/15…, 다국어 76/78/85… */
    private final String contentTypeId;

    private final String title;
    private final String address;      // addr1 + addr2
    private final String zipcode;

    /** 대표 이미지(원본, 약 500x333). 없을 수 있음 */
    private final String imageUrl;
    /** 대표 이미지(썸네일, 약 150x100). 없을 수 있음 */
    private final String thumbnailUrl;

    /** 저작권 유형 — Type1: 출처표시, Type3: 출처표시+변경금지 */
    private final String copyrightType;

    private final Double mapX;         // 경도(WGS84)
    private final Double mapY;         // 위도(WGS84)

    /** 분류체계 대/중/소 — NA(자연), FD(음식), EV(축제행사) 등 */
    private final String lclsSystm1;
    private final String lclsSystm2;
    private final String lclsSystm3;

    private final String createdTime;
    private final String modifiedTime;

    // ── searchFestival2 전용 ────────────────────────────────
    /** 행사 시작일 (yyyyMMdd) */
    private final String eventStartDate;
    /** 행사 종료일 (yyyyMMdd) */
    private final String eventEndDate;

    // ── locationBasedList2 전용 ─────────────────────────────
    /** 중심 좌표로부터의 거리(m) */
    private final Double distance;

    /** 대표 이미지 보유 여부 */
    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    /**
     * 이미지 2차 가공(크롭·필터 등) 가능 여부.
     * Type3 은 "제1유형 + 변경금지" 이므로 원본 그대로만 사용해야 한다.
     */
    public boolean isImageModifiable() {
        return !"Type3".equalsIgnoreCase(copyrightType);
    }
}
