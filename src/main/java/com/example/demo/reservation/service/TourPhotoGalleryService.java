package com.example.demo.reservation.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class TourPhotoGalleryService {

    private static final List<List<String>> BUSAN_HERO_KEYWORD_GROUPS = List.of(
            List.of("태종대", "오륙도", "송도해수욕장"),
            List.of("광안대교", "광안리", "해운대"),
            List.of("감천문화마을", "해동용궁사", "부산타워")
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Optional<String>> imageCache = new ConcurrentHashMap<>();

    @Value("${photoapi.base-url}")
    private String baseUrl;

    @Value("${photoapi.service-key:}")
    private String serviceKey;

    @Value("${photoapi.search-path:/gallerySearchList1}")
    private String searchPath;

    @Value("${tourapi.mobile-os:ETC}")
    private String mobileOs;

    @Value("${tourapi.mobile-app:KoreaPoint}")
    private String mobileApp;

    public Optional<String> findImageUrl(String placeTitle) {
        String keyword = normalize(placeTitle);
        if (keyword.isBlank() || serviceKey == null || serviceKey.isBlank()) {
            return Optional.empty();
        }
        return imageCache.computeIfAbsent(keyword, this::requestImageUrl);
    }

    public List<String> findBusanHeroImageUrls(int count) {
        if (count <= 0 || serviceKey == null || serviceKey.isBlank()) {
            return List.of();
        }

        Set<String> imageUrls = new LinkedHashSet<>();
        List<List<String>> keywordGroups = new ArrayList<>(BUSAN_HERO_KEYWORD_GROUPS);
        Collections.shuffle(keywordGroups);

        // Select at most one image per landmark group so consecutive shots of
        // the same place cannot occupy multiple hero slides.
        for (List<String> keywordGroup : keywordGroups) {
            List<String> keywords = new ArrayList<>(keywordGroup);
            Collections.shuffle(keywords);
            String keyword = keywords.get(0);
            int pageNo = ThreadLocalRandom.current().nextInt(1, 4);
            List<String> candidates = new ArrayList<>(requestImageUrls(keyword, 20, pageNo));
            Collections.shuffle(candidates);
            candidates.stream()
                    .filter(imageUrl -> !imageUrls.contains(imageUrl))
                    .findFirst()
                    .ifPresent(imageUrls::add);
            if (imageUrls.size() >= count) {
                break;
            }
        }

        List<String> result = new ArrayList<>(imageUrls);
        Collections.shuffle(result);
        return result.stream().limit(count).toList();
    }

    private Optional<String> requestImageUrl(String keyword) {
        return requestImageUrls(keyword, 5, 1).stream().findFirst();
    }

    private List<String> requestImageUrls(String keyword, int numOfRows, int pageNo) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + searchPath)
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", mobileOs)
                .queryParam("MobileApp", mobileApp)
                .queryParam("_type", "json")
                .queryParam("arrange", "A")
                .queryParam("numOfRows", Math.max(numOfRows, 1))
                .queryParam("pageNo", Math.max(pageNo, 1))
                .queryParam("keyword", keyword)
                .build(false)
                .encode(StandardCharsets.UTF_8)
                .toUri();

        try {
            JsonNode root = restTemplate.getForObject(uri, JsonNode.class);
            return imageUrls(root);
        } catch (RestClientException e) {
            log.warn("PhotoGallery request failed: {}", uri, e);
            return List.of();
        }
    }

    private List<String> imageUrls(JsonNode root) {
        JsonNode itemNode = root == null ? null : root.at("/response/body/items/item");
        if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }

        List<String> imageUrls = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode item : itemNode) {
                String imageUrl = text(item, "galWebImageUrl");
                if (!imageUrl.isBlank()) {
                    imageUrls.add(imageUrl);
                }
            }
            return imageUrls;
        }

        String imageUrl = text(itemNode, "galWebImageUrl");
        return imageUrl.isBlank() ? List.of() : List.of(imageUrl);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
