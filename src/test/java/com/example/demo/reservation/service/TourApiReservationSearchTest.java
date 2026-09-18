package com.example.demo.reservation.service;

import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.tourism.dto.TourPlaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TourApiReservationSearchTest {
    private TourApiReservationService service;
    private MockRestServiceServer server;
    private TourPlaceI18n i18n;

    @BeforeEach
    void setUp() {
        i18n = mock(TourPlaceI18n.class);
        service = new TourApiReservationService(i18n, mock(ReservationDetailTranslationService.class));
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test/KorService2");
        ReflectionTestUtils.setField(service, "listPath", "/areaBasedList2");
        ReflectionTestUtils.setField(service, "serviceKey", "test-key");
        ReflectionTestUtils.setField(service, "areaCode", "6");
        ReflectionTestUtils.setField(service, "mobileOs", "ETC");
        ReflectionTestUtils.setField(service, "mobileApp", "KoreaPoint");
        server = MockRestServiceServer.bindTo((RestTemplate)
                ReflectionTestUtils.getField(service, "restTemplate")).build();
    }

    @Test
    void findsTitleAndAddressBeyondFirstSourcePage() {
        expectPage(1, nonmatchingPage());
        expectPage(2, item("101", "해운대 해수욕장", "부산") + ","
                + item("102", "미술관", "부산 해운대구"));

        assertThat(service.findPlaces(" 해운대 ", "tourist_spot", "ko", 1, 30))
                .extracting(place -> place.getContentId()).containsExactly("101", "102");
        server.verify();
    }

    @Test
    void paginatesMatchesInsteadOfSourcePages() {
        expectPage(1, nonmatchingPage());
        expectPage(2, item("101", "해운대 해수욕장", "부산") + ","
                + item("102", "미술관", "부산 해운대구") + ","
                + item("103", "해운대 공원", "부산"));

        assertThat(service.findPlaces("해운대", "tourist_spot", "ko", 2, 2))
                .extracting(place -> place.getContentId()).containsExactly("103");
        server.verify();
    }

    @Test
    void returnsEmptyAfterSearchingAllPages() {
        expectPage(1, nonmatchingPage());
        expectPage(2, item("101", "공원", "부산"));
        assertThat(service.findPlaces("없는 장소", "tourist_spot", "ko", 1, 30)).isEmpty();
        server.verify();
    }

    @Test
    void searchesLocalizedNamesCaseInsensitively() {
        doAnswer(call -> {
            TourPlaceDto place = call.getArgument(1);
            place.setTitle("Haeundae Beach");
            return null;
        }).when(i18n).apply(eq("en"), any(TourPlaceDto.class));
        expectPage(1, item("101", "해운대 해수욕장", "부산"));
        expectPage(1, ""); // Official-language overlay is optional.
        assertThat(service.findPlaces("HAEUNDAE", "tourist_spot", "en", 1, 30))
                .extracting(place -> place.getContentId()).containsExactly("101");
        server.verify();
    }

    @Test
    void blankSearchKeepsNormalPagination() {
        server.expect(queryParam("pageNo", "2")).andExpect(queryParam("numOfRows", "30"))
                .andRespond(withSuccess(response(item("31", "공원", "부산")), MediaType.APPLICATION_JSON));
        assertThat(service.findPlaces("  ", "tourist_spot", "ko", 2, 30)).hasSize(1);
        server.verify();
    }

    private void expectPage(int page, String items) {
        server.expect(queryParam("pageNo", String.valueOf(page)))
                .andExpect(queryParam("numOfRows", "100"))
                .andExpect(queryParam("areaCode", "6"))
                .andRespond(withSuccess(response(items), MediaType.APPLICATION_JSON));
    }

    private String nonmatchingPage() {
        return IntStream.rangeClosed(1, 100).mapToObj(i -> item(String.valueOf(i), "공원", "부산"))
                .collect(Collectors.joining(","));
    }

    private String item(String id, String title, String address) {
        return "{\"contentid\":\"" + id + "\",\"contenttypeid\":\"12\",\"title\":\""
                + title + "\",\"addr1\":\"" + address + "\"}";
    }

    private String response(String items) {
        return "{\"response\":{\"body\":{\"items\":{\"item\":[" + items + "]}}}}";
    }
}
