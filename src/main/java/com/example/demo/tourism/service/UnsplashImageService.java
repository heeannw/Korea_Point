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
 * [수정] 카테고리당 10장 배열로 캐싱
 *   - count=10 파라미터 1회 호출로 10장 수신 → API 호출 횟수 동일(8번)
 *   - 카드 목록: small URL (썸네일용, 빠른 로딩)
 *   - 상세 패널: regular URL (1080px, 고화질)
 *   - JS에서 장소 index % 10 으로 분배 → 이미지 다양화
 *
 * application.properties 에 아래 설정 필요:
 *   unsplash.access-key=YOUR_UNSPLASH_ACCESS_KEY
 */
@Service
public class UnsplashImageService {

    private static final int IMAGE_COUNT = 10; // 카테고리당 가져올 이미지 수

    @Value("${unsplash.access-key}")
    private String accessKey;

    /**
     * 카테고리 → 썸네일(small) URL 리스트 캐시
     * key: "카페", "한식" 등
     * value: 최대 10개 URL 리스트
     */
    private final Map<String, List<String>> thumbCache = new HashMap<>();

    /**
     * 카테고리 → 고화질(regular) URL 리스트 캐시 (상세 패널용)
     */
    private final Map<String, List<String>> regularCache = new HashMap<>();

    /** 카테고리 → Unsplash 검색 키워드 매핑 */
    private static final Map<String, String> KEYWORD_MAP = new LinkedHashMap<>();
    static {
        KEYWORD_MAP.put("카페",       "coffee cafe interior");
        KEYWORD_MAP.put("한식",       "korean food bibimbap");
        KEYWORD_MAP.put("중식",       "chinese food dim sum");
        KEYWORD_MAP.put("일식",       "japanese food sushi ramen");
        KEYWORD_MAP.put("양식",       "western food pasta steak");
        KEYWORD_MAP.put("고기_해산물", "grilled meat seafood bbq");
        KEYWORD_MAP.put("바",         "cocktail bar drink night");
        KEYWORD_MAP.put("기타",       "restaurant food dining");
    }

    /** 카테고리별 폴백 썸네일 목록 (Unsplash API 실패 시 사용) */
    private static final Map<String, List<String>> FALLBACK_THUMB = new LinkedHashMap<>();
    static {
        FALLBACK_THUMB.put("카페", Arrays.asList(
                "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400",
                "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=400",
                "https://images.unsplash.com/photo-1442512595331-e89e73853f31?w=400",
                "https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=400",
                "https://images.unsplash.com/photo-1554118811-1e0d58224f24?w=400"
        ));
        FALLBACK_THUMB.put("한식", Arrays.asList(
                "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400",
                "https://images.unsplash.com/photo-1583623025817-d180a2221d0a?w=400",
                "https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?w=400",
                "https://images.unsplash.com/photo-1547592180-85f173990554?w=400",
                "https://images.unsplash.com/photo-1590301157890-4810ed352733?w=400"
        ));
        FALLBACK_THUMB.put("중식", Arrays.asList(
                "https://images.unsplash.com/photo-1563245372-f21724e3856d?w=400",
                "https://images.unsplash.com/photo-1548943487-a2e4e43b4853?w=400",
                "https://images.unsplash.com/photo-1552611052-33e04de081de?w=400",
                "https://images.unsplash.com/photo-1534482421-64566f976cfa?w=400",
                "https://images.unsplash.com/photo-1515669097368-22e68427d265?w=400"
        ));
        FALLBACK_THUMB.put("일식", Arrays.asList(
                "https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=400",
                "https://images.unsplash.com/photo-1569050467447-ce54b3bbc37d?w=400",
                "https://images.unsplash.com/photo-1617196034183-421b4040ed20?w=400",
                "https://images.unsplash.com/photo-1611143669185-af224c5e3252?w=400",
                "https://images.unsplash.com/photo-1582450871972-ab5ca641643d?w=400"
        ));
        FALLBACK_THUMB.put("양식", Arrays.asList(
                "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=400",
                "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?w=400",
                "https://images.unsplash.com/photo-1432139509613-5c4255815697?w=400",
                "https://images.unsplash.com/photo-1473093295043-cdd812d0e601?w=400",
                "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400"
        ));
        FALLBACK_THUMB.put("고기_해산물", Arrays.asList(
                "https://images.unsplash.com/photo-1544025162-d76694265947?w=400",
                "https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=400",
                "https://images.unsplash.com/photo-1558030006-450675393462?w=400",
                "https://images.unsplash.com/photo-1565557623262-b51c2513a641?w=400",
                "https://images.unsplash.com/photo-1510130387422-82bed34b37e9?w=400"
        ));
        FALLBACK_THUMB.put("바", Arrays.asList(
                "https://images.unsplash.com/photo-1514362545857-3bc16c4c7d1b?w=400",
                "https://images.unsplash.com/photo-1572116469696-31de0f17cc34?w=400",
                "https://images.unsplash.com/photo-1470337458703-46ad1756a187?w=400",
                "https://images.unsplash.com/photo-1527529482837-4698179dc6ce?w=400",
                "https://images.unsplash.com/photo-1566417713940-fe7c737a9ef2?w=400"
        ));
        FALLBACK_THUMB.put("기타", Arrays.asList(
                "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?w=400",
                "https://images.unsplash.com/photo-1550966871-3ed3cdb5ed0c?w=400",
                "https://images.unsplash.com/photo-1559339352-11d035aa65de?w=400",
                "https://images.unsplash.com/photo-1537047902294-62a40c20a6ae?w=400",
                "https://images.unsplash.com/photo-1424847651672-bf20a4b0982b?w=400"
        ));
    }

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 서버 시작 시 모든 카테고리 이미지를 미리 로딩합니다. (카테고리당 10장)
     * API 호출 횟수: 8번 (카테고리 수와 동일, count=10으로 한 번에 10장 수신)
     */
    @PostConstruct
    public void preloadAllCategories() {
        System.out.println("🖼️ UnsplashImageService: 카테고리별 이미지 사전 로딩 시작 (각 " + IMAGE_COUNT + "장)...");
        for (String category : KEYWORD_MAP.keySet()) {
            fetchAndCacheImages(category);
            System.out.println("  ✅ [" + category + "] 썸네일 " + thumbCache.getOrDefault(category, List.of()).size()
                    + "장 / 고화질 " + regularCache.getOrDefault(category, List.of()).size() + "장 로딩");
            // Unsplash API rate limit 방지
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }
        System.out.println("🖼️ UnsplashImageService: 사전 로딩 완료 (" + thumbCache.size() + "개 카테고리)");
    }

    /**
     * 카테고리의 썸네일 URL 리스트 반환 (카드 목록용).
     * JS에서 index % list.length 로 분배
     */
    public List<String> getThumbList(String category) {
        if (category == null || category.isBlank()) category = "기타";
        if (!thumbCache.containsKey(category)) fetchAndCacheImages(category);
        List<String> list = thumbCache.get(category);
        return (list != null && !list.isEmpty()) ? list : FALLBACK_THUMB.getOrDefault(category, FALLBACK_THUMB.get("기타"));
    }

    /**
     * 카테고리의 고화질 URL 리스트 반환 (상세 패널용).
     */
    public List<String> getRegularList(String category) {
        if (category == null || category.isBlank()) category = "기타";
        if (!regularCache.containsKey(category)) fetchAndCacheImages(category);
        List<String> list = regularCache.get(category);
        return (list != null && !list.isEmpty()) ? list : getThumbList(category);
    }

    /**
     * 모든 카테고리의 썸네일 리스트 맵 반환 (Thymeleaf → JS 주입용).
     * JS: FOOD_IMAGE_MAP["한식"][index % FOOD_IMAGE_MAP["한식"].length]
     */
    public Map<String, List<String>> getAllThumbLists() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String category : KEYWORD_MAP.keySet()) {
            result.put(category, getThumbList(category));
        }
        return result;
    }

    /**
     * 모든 카테고리의 고화질 리스트 맵 반환 (상세 패널용).
     */
    public Map<String, List<String>> getAllRegularLists() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String category : KEYWORD_MAP.keySet()) {
            result.put(category, getRegularList(category));
        }
        return result;
    }

    // ── 단일 카테고리 이미지 일괄 로드 ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void fetchAndCacheImages(String category) {
        String keyword  = KEYWORD_MAP.getOrDefault(category, "food restaurant");
        List<String> fbThumb = FALLBACK_THUMB.getOrDefault(category, FALLBACK_THUMB.get("기타"));

        try {
            // count=10: 한 번 호출로 10장 수신 → API 횟수 절약
            String url = "https://api.unsplash.com/photos/random"
                    + "?query="  + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                    + "&count="  + IMAGE_COUNT
                    + "&orientation=landscape"
                    + "&client_id=" + accessKey;

            // count > 1 이면 배열 반환
            List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
            if (response == null || response.isEmpty()) {
                thumbCache.put(category, fbThumb);
                regularCache.put(category, fbThumb);
                return;
            }

            List<String> thumbs   = new ArrayList<>();
            List<String> regulars = new ArrayList<>();
            for (Map<String, Object> photo : response) {
                Map<String, Object> urls = (Map<String, Object>) photo.get("urls");
                if (urls == null) continue;
                String small   = (String) urls.get("small");    // 썸네일 (~400px)
                String regular = (String) urls.get("regular");  // 고화질 (~1080px)
                if (small   != null && !small.isBlank())   thumbs.add(small);
                if (regular != null && !regular.isBlank()) regulars.add(regular);
            }

            thumbCache.put(category,   thumbs.isEmpty()   ? fbThumb : thumbs);
            regularCache.put(category, regulars.isEmpty() ? fbThumb : regulars);

        } catch (Exception e) {
            System.err.println("⚠️ Unsplash API 실패 [" + category + "]: " + e.getMessage());
            thumbCache.put(category,   fbThumb);
            regularCache.put(category, fbThumb);
        }
    }

    // ── 하위 호환: 단일 URL 반환 (기존 카페 컨트롤러용) ─────────────────────

    /** 카테고리 썸네일 0번째 URL 반환 */
    public String getImageUrl(String category) {
        List<String> list = getThumbList(category);
        return list.isEmpty() ? "" : list.get(0);
    }

    /** 하위 호환: 단일 URL 맵 반환 */
    public Map<String, String> getAllImageUrls() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String category : KEYWORD_MAP.keySet()) {
            result.put(category, getImageUrl(category));
        }
        return result;
    }
}