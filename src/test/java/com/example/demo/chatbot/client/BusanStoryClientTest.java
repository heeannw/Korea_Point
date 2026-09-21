package com.example.demo.chatbot.client;

import com.example.demo.chatbot.config.BusanStoryProperties;
import com.example.demo.chatbot.dto.BusanStoryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 부산 스토리텔링 API 는 멀쩡하다가도 간헐적으로 504 를 낸다(실측: 같은 요청 20회 중 2회).
 *
 * <p>확인하려는 것은 하나다. <b>저쪽이 한 번 삐끗했을 때 챗봇이 6시간 동안 죽지 않는가.</b>
 * 예전에는 실패를 빈 목록으로 바꿔 캐시해서, 한 번 실패하면 캐시가 살아 있는 내내 API 를
 * 다시 불러 보지도 않고 "관련된 부산 이야기를 찾지 못했어요" 만 답했다.
 */
class BusanStoryClientTest {

    private static final String PAGE_1 = """
            {"getBusanCultureStoryTelling":{"totalCount":2,"item":[
              {"title":"범어사","contents":"금빛 물고기 이야기","imgUrl":"http://x/getImage.do?atchFileId=FILE_1"},
              {"title":"흰여울 마을","contents":"바닷가 벽화마을","imgUrl":"http://x/getImage.do?atchFileId=FILE_2"}
            ]}}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BusanStoryProperties properties() {
        BusanStoryProperties p = new BusanStoryProperties();
        p.setServiceKey("test-key");
        p.setRowsPerPage(100);   // 한 쪽에 다 들어간다
        p.setCacheMinutes(360);
        return p;
    }

    private record Fixture(BusanStoryClient client, MockRestServiceServer server) {}

    /** 기대값은 적어 둔 순서대로 물린다. 그래서 "처음엔 504, 그다음엔 성공" 을 그대로 흉내 낼 수 있다. */
    private Fixture fixture(BusanStoryProperties props) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        return new Fixture(new BusanStoryClient(props, objectMapper, restTemplate), server);
    }

    private Fixture fixture() {
        return fixture(properties());
    }

    @Test
    void 정상이면_이야기를_받아온다() {
        Fixture f = fixture();
        f.server().expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PAGE_1, MediaType.APPLICATION_JSON));

        List<BusanStoryDto> stories = f.client().findAll();

        assertThat(stories).hasSize(2);
        assertThat(stories.get(0).getTitle()).isEqualTo("범어사");
    }

    @Test
    void 한_번_성공하면_다음부터는_다시_부르지_않는다() {
        // 캐시는 그대로 살아 있어야 한다 — 호출 한도를 아끼려고 둔 것이다.
        Fixture f = fixture();
        f.server().expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PAGE_1, MediaType.APPLICATION_JSON));

        f.client().findAll();
        f.client().findAll();

        f.server().verify();   // 딱 한 번만 불렀어야 한다
    }

    @Test
    void 한_번_삐끗해도_다음_질문에서_다시_시도한다() {
        // 이게 이번에 고친 핵심이다. 예전에는 빈 목록이 6시간 캐시돼서 여기서 영영 멈췄다.
        Fixture f = fixture();
        // 첫 요청: 재시도까지 3번 모두 504
        f.server().expect(ExpectedCount.times(3), anything())
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        // 그다음 질문에서 다시 부르면 성공
        f.server().expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PAGE_1, MediaType.APPLICATION_JSON));

        assertThat(f.client().findAll()).isEmpty();          // 이번 질문은 답을 못 한다
        assertThat(f.client().findAll()).hasSize(2);         // 그러나 바로 다음 질문은 된다
    }

    @Test
    void 한_쪽이_504여도_곧바로_재시도해서_살린다() {
        // 저쪽 504 는 곧바로 다시 부르면 대개 성공한다. 한 번 튕겼다고 포기하면 안 된다.
        Fixture f = fixture();
        f.server().expect(ExpectedCount.once(), anything())
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        f.server().expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PAGE_1, MediaType.APPLICATION_JSON));

        assertThat(f.client().findAll()).hasSize(2);
    }

    @Test
    void 갱신에_실패하면_직전_성공본으로_버틴다() {
        // 캐시가 만료된 뒤 갱신이 실패한 상황. 화면이 빈손이 되는 대신 어제 받은 목록으로 답한다.
        BusanStoryProperties props = properties();
        props.setCacheMinutes(0);   // 캐시를 끄면 매번 새로 받아온다
        Fixture f = fixture(props);

        // 기대값은 요청이 나가기 전에 모두 적어 둔다(첫 번째는 성공, 그다음 세 번은 504).
        f.server().expect(ExpectedCount.once(), anything())
                .andRespond(withSuccess(PAGE_1, MediaType.APPLICATION_JSON));
        f.server().expect(ExpectedCount.times(3), anything())
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThat(f.client().findAll()).hasSize(2);
        assertThat(f.client().findAll()).hasSize(2);   // 갱신은 실패했지만 직전 것으로 답한다
    }
}
