package com.example.demo.tourism.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관광공사 외국어 목록의 제목에서 국문 원문을 갈라내는 부분.
 *
 * <p>국문과 외국어 서비스는 contentId 체계가 달라(부산 기준 교집합 0건) 번호로는 못 맞춘다.
 * 대신 외국어 제목에 국문 원문이 붙어 오는 것을 열쇠로 쓴다 — 여기가 틀리면 외국어 화면의
 * 장소 이름이 통째로 DeepL 기계번역으로 떨어진다. 예시는 전부 실제 응답에서 가져왔다.</p>
 */
class TourPlaceI18nSplitTitleTest {

    @Test
    void 괄호로_붙여_온_국문을_갈라낸다() {
        TourPlaceI18n.Split split = TourPlaceI18n.splitTitle("Haeundae Beach (해운대해수욕장)");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("Haeundae Beach");
        assertThat(split.koreanName()).isEqualTo("해운대해수욕장");
        assertThat(split.strippedName()).isNull();
    }

    @Test
    void 전각_괄호도_같이_본다() {
        TourPlaceI18n.Split split = TourPlaceI18n.splitTitle("海雲台 冬柏島（해운대 동백섬）");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("海雲台 冬柏島");
        assertThat(split.koreanName()).isEqualTo("해운대 동백섬");
    }

    @Test
    void 빗금으로_붙여_온_것도_갈라낸다() {
        TourPlaceI18n.Split split = TourPlaceI18n.splitTitle("KOLON SEACLOUD HOTEL / 코오롱씨클라우드호텔");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("KOLON SEACLOUD HOTEL");
        assertThat(split.koreanName()).isEqualTo("코오롱씨클라우드호텔");
    }

    @Test
    void 구분자_없이_그냥_붙여_온_것도_갈라낸다() {
        // 중국어 목록에 열 줄쯤 이렇게 온다. 광안리해수욕장·자갈치시장이 여기 끼어 있었다.
        TourPlaceI18n.Split split = TourPlaceI18n.splitTitle("广安里海水浴场광안리해수욕장");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("广安里海水浴场");
        assertThat(split.koreanName()).isEqualTo("광안리해수욕장");
    }

    @Test
    void 대괄호를_구분자로_쓴_줄도_갈라낸다() {
        TourPlaceI18n.Split split =
                TourPlaceI18n.splitTitle("松岛云端散步路（天空步道） [송도 구름산책로(스카이워크)]");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("松岛云端散步路（天空步道）");
        assertThat(split.koreanName()).isEqualTo("송도 구름산책로(스카이워크)");
        assertThat(split.strippedName()).isEqualTo("송도 구름산책로");
    }

    @Test
    void 이름_뒤에_붙은_대괄호_표시는_떼어낸다() {
        // 부산 영어 목록 1130곳 중 895곳이 사후면세점 표시를 달고 온다.
        TourPlaceI18n.Split split =
                TourPlaceI18n.splitTitle("GAJIN-HONSU [Tax Refund Shop](가진혼수이불)");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("GAJIN-HONSU");
        assertThat(split.koreanName()).isEqualTo("가진혼수이불");
    }

    @Test
    void 이름_앞에_붙은_대괄호_표시도_떼어낸다() {
        // 일본어·중국어는 같은 표시를 이름 앞에 붙인다.
        TourPlaceI18n.Split split =
                TourPlaceI18n.splitTitle("[事后免税店]架轸寝具用品(가진혼수이불)");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("架轸寝具用品");
        assertThat(split.koreanName()).isEqualTo("가진혼수이불");
    }

    @Test
    void 국문이_부연을_달고_오면_벗긴_짝을_같이_만든다() {
        // 우리 국문 목록에는 그냥 '이기대'로 들어 있어서, 벗긴 열쇠가 없으면 못 맞춘다.
        // 그때 보여줄 이름도 부연 없는 짧은 쪽이라야 카드에 길게 늘어지지 않는다.
        TourPlaceI18n.Split split =
                TourPlaceI18n.splitTitle("Igidae Cliff [National Geopark] (이기대 (부산 국가지질공원))");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("Igidae Cliff");
        assertThat(split.strippedForeignTitle()).isEqualTo("Igidae Cliff");
        assertThat(split.koreanName()).isEqualTo("이기대 (부산 국가지질공원)");
        assertThat(split.strippedName()).isEqualTo("이기대");
    }

    @Test
    void 벗긴_짝은_이름에서도_부연을_뗀다() {
        TourPlaceI18n.Split split =
                TourPlaceI18n.splitTitle("二妓台（釜山国家地質公園）（이기대（부산 국가지질공원））");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("二妓台（釜山国家地質公園）");
        assertThat(split.strippedForeignTitle()).isEqualTo("二妓台");
        assertThat(split.strippedName()).isEqualTo("이기대");
    }

    @Test
    void 대괄호가_섞여_있어도_괄호_짝을_맞춘다() {
        TourPlaceI18n.Split split =
                TourPlaceI18n.splitTitle("Kim & Kimchi [Tax Refund Shop](김앤김치(KIM&KIMCHI))");

        assertThat(split).isNotNull();
        assertThat(split.foreignTitle()).isEqualTo("Kim & Kimchi");
        assertThat(split.koreanName()).isEqualTo("김앤김치(KIM&KIMCHI)");
        assertThat(split.strippedName()).isEqualTo("김앤김치");
    }

    @Test
    void 국문이_안_붙어_온_줄은_건드리지_않는다() {
        assertThat(TourPlaceI18n.splitTitle("Namparang Trail (Busan)")).isNull();
        assertThat(TourPlaceI18n.splitTitle("APLSHOP [Tax Refund Shop](APLSHOP)")).isNull();
        assertThat(TourPlaceI18n.splitTitle("F1963")).isNull();
        assertThat(TourPlaceI18n.splitTitle(null)).isNull();
    }

    @Test
    void 번역_없이_국문만_온_줄은_버린다() {
        // 앞이 통째로 비면 바꿔 끼울 이름이 없다. 앞뒤가 다 한글이면 어디까지가 번역인지 모른다.
        assertThat(TourPlaceI18n.splitTitle("부산국제영화제")).isNull();
        assertThat(TourPlaceI18n.splitTitle("(부산국제영화제)")).isNull();
        assertThat(TourPlaceI18n.splitTitle("부산 차이나타운 상해거리")).isNull();
        // 표시만 떼면 이름이 안 남는 줄도 버린다.
        assertThat(TourPlaceI18n.splitTitle("[事后免税店](가진혼수이불)")).isNull();
    }
}
