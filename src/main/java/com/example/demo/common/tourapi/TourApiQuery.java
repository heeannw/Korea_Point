package com.example.demo.common.tourapi;

import lombok.Builder;
import lombok.Getter;

/**
 * 한국관광공사 목록 조회 파라미터.
 *
 * <p>파라미터가 많고 대부분 선택값이라 빌더로 받는다.
 * 지역(부산)·페이지 크기 등은 기본값이 있으므로 필요한 것만 지정하면 된다.
 *
 * <pre>
 * TourApiQuery.of()                       // 부산 전체, 12건
 * TourApiQuery.of().contentTypeId("12")   // 부산 관광지
 *                  .withImageOnly()       // 대표 이미지 있는 것만
 * </pre>
 *
 * <p>⚠️ 구버전 파라미터(areaCode / sigunguCode / cat1~cat3)는 관광공사에서
 * 삭제 예정으로 표기되어 사용하지 않는다. 지역은 lDongRegnCd(법정동 시도코드),
 * 분류는 lclsSystm1~3(신 분류체계)을 쓴다.
 */
@Getter
@Builder(toBuilder = true)
public class TourApiQuery {

    /** 법정동 시도코드 — 부산 26, 서울 11 */
    public static final String BUSAN = "26";

    /** 정렬: 제목순 */          public static final String SORT_TITLE    = "A";
    /** 정렬: 수정일순(최신) */   public static final String SORT_MODIFIED = "C";
    /** 정렬: 등록일순 */         public static final String SORT_CREATED  = "D";
    /** 정렬: 수정일순 + 대표이미지 필수 */ public static final String SORT_MODIFIED_WITH_IMAGE = "Q";

    @Builder.Default private String lDongRegnCd = BUSAN;
    private String lDongSignguCd;      // 시군구코드 (lDongRegnCd 필수 동반)

    private String contentTypeId;      // 12 관광지 / 15 축제 / 39 음식점 …
    @Builder.Default private String arrange = SORT_MODIFIED;

    private String lclsSystm1;         // 분류 대분류 (NA, FD, EV …)
    private String lclsSystm2;
    private String lclsSystm3;

    @Builder.Default private int numOfRows = 12;
    @Builder.Default private int pageNo = 1;

    /** 언어 — ko/en/ja/zh-CN … (기본 ko) */
    @Builder.Default private String lang = TourApiLang.KO;

    // ── searchFestival2 전용 ───────────────────────────────
    private String eventStartDate;     // yyyyMMdd (필수)
    private String eventEndDate;

    // ── searchKeyword2 전용 ────────────────────────────────
    private String keyword;

    // ── locationBasedList2 전용 ────────────────────────────
    private Double mapX;
    private Double mapY;
    private Integer radius;            // m 단위, 최대 20000

    /** 기본값(부산·수정일순·12건)으로 시작 */
    public static TourApiQueryBuilder of() {
        return TourApiQuery.builder();
    }

    /**
     * 대표 이미지가 있는 항목만 조회한다.
     * 음식점은 약 1/3이 대표 이미지가 없으므로 카드형 UI에서는 이 옵션을 쓴다.
     */
    public TourApiQuery withImageOnly() {
        return toBuilder().arrange(SORT_MODIFIED_WITH_IMAGE).build();
    }

    /** 캐시 키 — 같은 조건이면 같은 문자열이 나오도록 모든 필드를 이어붙인다 */
    String cacheKey(String operation) {
        return String.join("|", operation, lang,
                nz(lDongRegnCd), nz(lDongSignguCd), nz(contentTypeId), nz(arrange),
                nz(lclsSystm1), nz(lclsSystm2), nz(lclsSystm3),
                String.valueOf(numOfRows), String.valueOf(pageNo),
                nz(eventStartDate), nz(eventEndDate), nz(keyword),
                String.valueOf(mapX), String.valueOf(mapY), String.valueOf(radius));
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }
}
