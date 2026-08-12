package com.example.demo.reservation.service;

import com.example.demo.reservation.dto.TourApiPlaceDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TourApiReservationService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final TourPhotoGalleryService tourPhotoGalleryService;

    @Value("${tourapi.base-url}")
    private String baseUrl;

    @Value("${tourapi.service-key:}")
    private String serviceKey;

    @Value("${tourapi.mobile-os:ETC}")
    private String mobileOs;

    @Value("${tourapi.mobile-app:KoreaPoint}")
    private String mobileApp;

    @Value("${tourapi.area-code:6}")
    private String areaCode;

    @Value("${tourapi.list-path:/areaBasedList1}")
    private String listPath;

    @Value("${tourapi.detail-path:/detailCommon1}")
    private String detailPath;

    public List<TourApiPlaceDto> findPlaces(String keyword, String contentTypeId, int pageNo, int numOfRows) {
        if (!hasServiceKey()) {
            return List.of();
        }

        UriComponentsBuilder builder = commonBuilder(baseUrl + listPath)
                .queryParam("pageNo", Math.max(pageNo, 1))
                .queryParam("numOfRows", Math.max(numOfRows, 1))
                .queryParam("arrange", "A")
                .queryParam("areaCode", areaCode);

        if (contentTypeId != null && !contentTypeId.isBlank()) {
            builder.queryParam("contentTypeId", contentTypeId);
        }

        URI uri = builder.build(true).toUri();

        JsonNode root = request(uri);
        List<TourApiPlaceDto> places = parseItems(root);
        if (keyword == null || keyword.isBlank()) {
            return places;
        }

        String normalized = keyword.trim().toLowerCase();
        return places.stream()
                .filter(place -> contains(place.getTitle(), normalized) || contains(place.getAddress(), normalized))
                .toList();
    }

    public TourApiPlaceDto findDetail(String contentId, String contentTypeId) {
        if (!hasServiceKey() || contentId == null || contentId.isBlank()) {
            return null;
        }

        UriComponentsBuilder builder = commonBuilder(baseUrl + detailPath)
                .queryParam("contentId", contentId)
                .queryParam("defaultYN", "Y")
                .queryParam("firstImageYN", "Y")
                .queryParam("addrinfoYN", "Y")
                .queryParam("mapinfoYN", "Y")
                .queryParam("overviewYN", "Y");

        if (contentTypeId != null && !contentTypeId.isBlank()) {
            builder.queryParam("contentTypeId", contentTypeId);
        }

        List<TourApiPlaceDto> items = parseItems(request(builder.build(true).toUri()));
        return items.isEmpty() ? null : items.get(0);
    }

    public boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    private UriComponentsBuilder commonBuilder(String url) {
        return UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json");
    }

    private JsonNode request(URI uri) {
        try {
            return restTemplate.getForObject(uri, JsonNode.class);
        } catch (RestClientException e) {
            log.warn("TourAPI request failed: {}", uri, e);
            return null;
        }
    }

    private List<TourApiPlaceDto> parseItems(JsonNode root) {
        JsonNode itemNode = root == null ? null : root.at("/response/body/items/item");
        if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }

        List<TourApiPlaceDto> places = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(item -> places.add(toPlace(item)));
        } else if (itemNode.isObject()) {
            places.add(toPlace(itemNode));
        }
        return places;
    }

    private TourApiPlaceDto toPlace(JsonNode item) {
        String address = text(item, "addr1");
        String addr2 = text(item, "addr2");
        if (!addr2.isBlank()) {
            address = address.isBlank() ? addr2 : address + " " + addr2;
        }

        String title = text(item, "title");
        String imageUrl = firstText(item, "firstimage", "firstimage2");
        if (imageUrl.isBlank()) {
            imageUrl = tourPhotoGalleryService.findImageUrl(title).orElse("");
        }

        return TourApiPlaceDto.builder()
                .contentId(text(item, "contentid"))
                .contentTypeId(text(item, "contenttypeid"))
                .title(title)
                .address(address)
                .tel(text(item, "tel"))
                .imageUrl(imageUrl)
                .mapX(text(item, "mapx"))
                .mapY(text(item, "mapy"))
                .overview(text(item, "overview"))
                .build();
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value.isBlank() ? text(node, second) : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase().contains(keyword);
    }
}
