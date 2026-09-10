package com.example.demo.community.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 번역기에 보낼 본문을 다듬는 규칙을 확인한다.
 *
 * <p>이 규칙이 틀리면 두 방향으로 조용히 망가진다. 너무 많이 떼면 영어로 보는
 * 사람에게만 사진이 사라지고, 너무 적게 떼면 Base64 이미지 한 장 때문에 번역
 * 요청이 통째로 막혀 번역본이 아예 안 생긴다. 둘 다 국문 화면은 멀쩡해서
 * 늦게 발견된다. 그래서 여기서 잡는다.
 */
class TranslationServiceTest {

    @Test
    void 주소로_걸린_사진은_남긴다() {
        String body = "<p>감천 다녀왔습니다.</p>"
                + "<img src=\"https://tong.visitkorea.or.kr/cms/resource/89/3575989_image2_1.jpg\">"
                + "<p>골목마다 색이 다릅니다.</p>";

        assertThat(TranslationService.forTranslation(body)).isEqualTo(body);
    }

    @Test
    void 본문에_박힌_Base64_사진은_뗀다() {
        String body = "<p>앞</p>"
                + "<img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB\">"
                + "<p>뒤</p>";

        assertThat(TranslationService.forTranslation(body)).isEqualTo("<p>앞</p><p>뒤</p>");
    }

    @Test
    void 대문자와_공백이_섞여도_Base64_사진을_알아본다() {
        String body = "<p>앞</p><IMG  SRC = 'data:image/jpeg;base64,AAAA' alt=\"x\" /><p>뒤</p>";

        assertThat(TranslationService.forTranslation(body)).isEqualTo("<p>앞</p><p>뒤</p>");
    }

    @Test
    void 두_종류가_섞이면_박힌_것만_뗀다() {
        String url = "<img src=\"https://example.com/a.jpg\">";
        String body = "<p>글</p>" + url
                + "<img src=\"data:image/png;base64,AAAA\">"
                + "<p>끝</p>";

        assertThat(TranslationService.forTranslation(body)).isEqualTo("<p>글</p>" + url + "<p>끝</p>");
    }

    @Test
    void 사진만_있는_글은_빈_문자열이_된다() {
        // 호출한 쪽에서 빈 문자열이면 번역을 건너뛴다.
        assertThat(TranslationService.forTranslation("<img src=\"data:image/png;base64,AAAA\">"))
                .isEmpty();
    }
}
