package com.example.demo.tourism.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * UnsplashImageService
 *
 * 카테고리별로 Unsplash 이미지를 1회만 호출하고 메모리에 캐싱합니다.
 *
 * 카테고리 → 검색 키워드 매핑:
 *   카페        → "coffee cafe"
 *   한식        → "korean food"
 *   중식        → "chinese food"
 *   일식        → "japanese food sushi"
 *   양식        → "western food pasta"
 *   고기_해산물  → "grilled meat seafood"
 *   바          → "cocktail bar"
 *   기타        → "restaurant food"
 *
 * 사용법:
 *   String imageUrl = unsplashImageService.getImageUrl("한식");
 *   → Unsplash CDN URL 반환 (캐시 히트 시 즉시 반환, 최초 1회만 API 호출)
 *
 * application.properties 에 아래 설정 필요:
 *   unsplash.access-key=YOUR_UNSPLASH_ACCESS_KEY
 */
@Service
public class UnsplashImageService {

    @Value("${unsplash.access-key}")
    private String accessKey;

    /** 카테고리 → Unsplash 이미지 URL 캐시 (서버 시작 후 영구 보존) */
    private final Map<String, String> imageCache = new HashMap<>();

    /** 카테고리 → Unsplash 검색 키워드 매핑 */
    private static final Map<String, String> KEYWORD_MAP = new LinkedHashMap<>();
    static {
        KEYWORD_MAP.put("카페",       "coffee cafe");
        KEYWORD_MAP.put("한식",       "korean food");
        KEYWORD_MAP.put("중식",       "chinese food");
        KEYWORD_MAP.put("일식",       "japanese food sushi");
        KEYWORD_MAP.put("양식",       "western food pasta");
        KEYWORD_MAP.put("고기_해산물", "grilled meat seafood");
        KEYWORD_MAP.put("바",         "cocktail bar drink");
        KEYWORD_MAP.put("기타",       "restaurant food");
    }

    /** 카테고리별 폴백 이미지 (Unsplash API 실패 시 사용) */
    private static final Map<String, String> FALLBACK_MAP = new LinkedHashMap<>();
    static {
        FALLBACK_MAP.put("카페",       "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400");
        FALLBACK_MAP.put("한식",       "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400");
        FALLBACK_MAP.put("중식",       "https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400");
        FALLBACK_MAP.put("일식",       "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400");
        FALLBACK_MAP.put("양식",       "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=400");
        FALLBACK_MAP.put("고기_해산물", "https://images.unsplash.com/photo-1544025162-d76694265947?w=400");
        FALLBACK_MAP.put("바",         "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=400");
        FALLBACK_MAP.put("기타",       "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=400");
    }

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 서버 시작 시 모든 카테고리 이미지를 미리 로딩합니다.
     * 이후 요청은 캐시에서 즉시 반환됩니다.
     */
    @PostConstruct
    public void preloadAllCategories() {
        System.out.println("🖼️ UnsplashImageService: 카테고리별 이미지 사전 로딩 시작...");
        for (String category : KEYWORD_MAP.keySet()) {
            String url = fetchFromUnsplash(category);
            imageCache.put(category, url);
            System.out.println("  ✅ [" + category + "] → " + url);
            // Unsplash API rate limit 방지 (50 req/hr free tier)
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        System.out.println("🖼️ UnsplashImageService: 사전 로딩 완료 (" + imageCache.size() + "개 카테고리)");
    }

    /**
     * 카테고리에 해당하는 이미지 URL 반환.
     * 캐시에 있으면 즉시 반환, 없으면 Unsplash API 호출 후 캐싱.
     *
     * @param category "카페" | "한식" | "중식" | "일식" | "양식" | "고기_해산물" | "바" | "기타"
     * @return Unsplash 이미지 URL
     */
    public String getImageUrl(String category) {
        if (category == null || category.isBlank()) {
            return FALLBACK_MAP.getOrDefault("기타", "");
        }
        return imageCache.computeIfAbsent(category, this::fetchFromUnsplash);
    }

    /**
     * 모든 카테고리의 이미지 URL 맵 반환 (Thymeleaf 모델에 통째로 넘길 때 사용).
     * @return { "카페": "https://...", "한식": "https://...", ... }
     */
    public Map<String, String> getAllImageUrls() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String category : KEYWORD_MAP.keySet()) {
            result.put(category, getImageUrl(category));
        }
        return result;
    }

    // ── 내부: Unsplash API 호출 ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String fetchFromUnsplash(String category) {
        String keyword = KEYWORD_MAP.getOrDefault(category, "food restaurant");
        String fallback = FALLBACK_MAP.getOrDefault(category, FALLBACK_MAP.get("기타"));

        try {
            // Unsplash Random Photo API (키워드 기반, 1장)
            String url = "https://api.unsplash.com/photos/random"
                    + "?query=" + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                    + "&orientation=landscape"
                    + "&client_id=" + accessKey;

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            if (response == null) return fallback;

            Map<String, Object> urls = (Map<String, Object>) response.get("urls");
            if (urls == null) return fallback;

            // "small" 사이즈 사용 (400px 수준, 빠른 로딩)
            String imgUrl = (String) urls.get("small");
            return (imgUrl != null && !imgUrl.isBlank()) ? imgUrl : fallback;

        } catch (Exception e) {
            System.err.println("⚠️ Unsplash API 실패 [" + category + "]: " + e.getMessage());
            return fallback;
        }
    }
}