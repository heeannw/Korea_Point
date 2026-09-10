package com.example.demo.community.controller;

import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.service.TourismService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 커뮤니티 글쓰기용 내부 장소 검색. 관광지·맛집·카페를 이름으로 매칭해 몇 개만 돌려준다.
 *
 * <p>세 목록 모두 {@link TourismService} 가 메모리에 들고 있는 {@link TourPlaceDto} 라
 * contentId 체계가 같아서, 뽑아 온 값을 그대로 {@code post_place} 에 저장하면 상세 패널
 * 역참조와 바로 맞물린다.
 */
@RestController
@RequestMapping("/community/api")
@RequiredArgsConstructor
public class CommunityPlaceSearchController {

    private final TourismService tourismService;

    public record PlaceHit(String contentId, String contentTypeId, String title,
                           String addr, String image, String kind) { }

    @GetMapping("/place-search")
    public List<PlaceHit> search(@RequestParam("q") String q,
                                 @RequestParam(value = "lang", defaultValue = "ko") String lang) {
        String needle = q == null ? "" : q.trim().toLowerCase();
        if (needle.length() < 2) return List.of();

        Set<String> seen = new LinkedHashSet<>();
        List<PlaceHit> hits = new ArrayList<>();
        collect(tourismService.allList(lang), needle, "tour", 6, seen, hits);
        collect(tourismService.foodList(lang), needle, "food", 4, seen, hits);
        collect(tourismService.cafeList(lang), needle, "cafe", 4, seen, hits);
        return hits;
    }

    private void collect(List<TourPlaceDto> source, String needle, String kind, int limit,
                         Set<String> seen, List<PlaceHit> out) {
        int added = 0;
        for (TourPlaceDto p : source) {
            if (added >= limit) break;
            String title = p.getTitle();
            if (title == null || !title.toLowerCase().contains(needle)) continue;
            if (p.getId() == null || !seen.add(p.getId())) continue;
            out.add(new PlaceHit(p.getId(), p.getType(), title, p.getAddr(), p.getImage(), kind));
            added++;
        }
    }
}
