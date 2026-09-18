package com.example.demo.tourism.config;

import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.client.TourApiClient.RelatedRow;
import com.example.demo.tourism.dto.TourPlaceDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 관광지별 "연관 관광지"(다른 여행자들의 실제 방문 동선 기반).
 *
 * <p>한국관광공사 TarRlteTarService1(부산 16개 구)을 서버가 직접, 주기적으로 호출해 메모리에 올려둔다.
 * 이 API는 응답의 장소명이 TourAPI contentId와 다른 "데이터랩 이름" 체계라, 이미 캐시해 쓰고 있는
 * 부산 전체 목록({@link TourApiClient#findBusanPlaces})을 이름표 삼아 contentId로 매칭한다.
 * 연관은 '관광지' 대분류만(음식·숙박 제외).</p>
 *
 * <p>정적 파일로 미리 받아 두지 않는다 — 관광공사 공모전 규정상 OpenAPI를 실시간으로 활용해야 하고
 * 파일 데이터 활용은 인정되지 않는다(문의처 답변 기준). 예전엔 사람이 스크립트를 돌려 파일로
 * 커밋해 두는 방식이었다({@code scripts/tourism/build_related_attractions.py}, 삭제됨).</p>
 */
@Component
public class RelatedAttractionData {

    private static final Logger log = LoggerFactory.getLogger(RelatedAttractionData.class);
    private static final int KEEP_PER_BASE = 8;   // 프론트에서는 3개만 노출
    /** 연관으로 남길 콘텐츠 유형: 관광지·문화시설·축제·레저. 쇼핑·음식·숙박·코스 제외. */
    private static final Set<String> KEEP_TYPES = Set.of("12", "14", "15", "28");
    private static final Pattern NORM_STRIP = Pattern.compile("[\\s()\\[\\]/·・\\-_,'’.]");
    private static final long REFRESH_MS = 24 * 60 * 60_000L; // 하루

    /** JSON 한 줄. */
    public record Related(String contentId, int rank) { }

    private final TourApiClient tourApiClient;
    private volatile Map<String, List<Related>> byContentId = Map.of();

    public RelatedAttractionData(TourApiClient tourApiClient) {
        this.tourApiClient = tourApiClient;
    }

    @PostConstruct
    @Scheduled(fixedRate = REFRESH_MS)
    void refresh() {
        try {
            Map<String, List<Related>> fresh = build();
            if (!fresh.isEmpty()) {
                byContentId = fresh;
                log.info("연관 관광지 로드: 기준 {}곳", fresh.size());
            }
        } catch (Exception e) {
            // 실패해도 이전에 성공한 데이터를 그대로 유지한다(비었으면 섹션이 안 보일 뿐).
            log.warn("연관 관광지 갱신 실패, 이전 데이터 유지", e);
        }
    }

    private Map<String, List<Related>> build() {
        Map<String, TourPlaceDto> byNormTitle = new HashMap<>();
        for (TourPlaceDto p : tourApiClient.findBusanPlaces("ko")) {
            byNormTitle.putIfAbsent(norm(p.getTitle()), p);
        }

        Map<String, Map<String, Integer>> byBase = new HashMap<>(); // baseId -> relId -> rank
        for (RelatedRow row : tourApiClient.findRelatedRows()) {
            TourPlaceDto base = match(byNormTitle, row.baseName());
            TourPlaceDto rel = match(byNormTitle, row.relatedName());
            if (base == null || rel == null) continue;
            if (base.getId().equals(rel.getId())) continue;
            if (!KEEP_TYPES.contains(rel.getType())) continue;
            byBase.computeIfAbsent(base.getId(), k -> new HashMap<>())
                    .merge(rel.getId(), row.rank(), Math::min); // 같은 연관지가 여러 번 나오면 더 높은(작은) 순위 유지
        }

        Map<String, List<Related>> result = new HashMap<>();
        byBase.forEach((baseId, relMap) -> result.put(baseId, relMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(KEEP_PER_BASE)
                .map(e -> new Related(e.getKey(), e.getValue()))
                .toList()));
        return result;
    }

    /** 정확히 일치하면 그걸 쓰고, 아니면 부분일치 후보가 단 하나뿐일 때만 쓴다. */
    private TourPlaceDto match(Map<String, TourPlaceDto> byNormTitle, String name) {
        String n = norm(name);
        if (n.isEmpty()) return null;
        TourPlaceDto exact = byNormTitle.get(n);
        if (exact != null) return exact;
        TourPlaceDto candidate = null;
        for (Map.Entry<String, TourPlaceDto> e : byNormTitle.entrySet()) {
            if (e.getKey().contains(n) || n.contains(e.getKey())) {
                if (candidate != null) return null; // 후보가 둘 이상이면 애매하니 포기
                candidate = e.getValue();
            }
        }
        return candidate;
    }

    private String norm(String s) {
        return s == null ? "" : NORM_STRIP.matcher(s).replaceAll("").toLowerCase();
    }

    /** rank 오름차순. 없으면 빈 리스트. */
    public List<Related> forContent(String contentId) {
        return byContentId.getOrDefault(contentId, List.of());
    }
}
