package com.example.demo.tourism.service;

import com.example.demo.tourism.client.TourApiClient;
import com.example.demo.tourism.config.TourPlaceI18n;
import com.example.demo.tourism.dto.SavedPlaceDto;
import com.example.demo.tourism.dto.TourPlaceDto;
import com.example.demo.tourism.entity.SavedPlace;
import com.example.demo.tourism.repository.SavedPlaceRepository;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관광지·음식점·카페 상세에서 누른 "저장(찜)"을 {@code saved_place}에 넣고 뺀다.
 *
 * <p>저장할 때 KorService2 detailCommon2로 국문 스냅샷(이름·주소·좌표·이미지)을 한 번 떠서
 * 함께 넣는다. 목록 조회 때 TourAPI를 다시 부르지 않기 위함이다.
 */
@Service
@RequiredArgsConstructor
public class SavedPlaceService {

    private final SavedPlaceRepository savedPlaceRepository;
    private final TourApiClient tourApiClient;
    private final TourPlaceI18n tourPlaceI18n;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<SavedPlaceDto> list(Long userId, String kind) {
        List<SavedPlace> rows = (kind == null || kind.isBlank())
                ? savedPlaceRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : savedPlaceRepository.findByUserIdAndKindOrderByCreatedAtDesc(userId, kind);
        Map<String, String> foodCategories = foodCategoriesFor(rows);
        String lang = currentLanguage();
        return rows.stream()
                .map(row -> localizedDto(row, displayKind(row, foodCategories), lang))
                .toList();
    }

    @Transactional(readOnly = true)
    public long count(Long userId) {
        return savedPlaceRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean isSaved(Long userId, String contentId) {
        return savedPlaceRepository.existsByUserIdAndContentId(userId, contentId);
    }

    /** 이미 저장돼 있으면 그 행을 그대로 돌려준다(멱등). */
    @Transactional
    public SavedPlaceDto save(Long userId, String contentId, String contentTypeIdHint, String kindHint) {
        SavedPlace existing = savedPlaceRepository.findByUserIdAndContentId(userId, contentId).orElse(null);
        if (existing != null) {
            String resolvedKind = resolveKind(existing.getContentTypeId(), null, kindHint);
            if (resolvedKind != null && !resolvedKind.equals(existing.getKind())) {
                existing.setKind(resolvedKind);
            }
            return SavedPlaceDto.from(existing);
        }

        TourPlaceDto snapshot = tourApiClient.findKoreanSnapshot(contentId);
        String contentTypeId = snapshot != null && snapshot.getType() != null
                ? snapshot.getType() : contentTypeIdHint;

        SavedPlace saved = SavedPlace.builder()
                .user(userRepository.getReferenceById(userId))
                .contentId(contentId)
                .contentTypeId(contentTypeId)
                .kind(resolveKind(contentTypeId, snapshot, kindHint))
                .title(snapshot != null ? snapshot.getTitle() : null)
                .addr(snapshot != null ? snapshot.getAddr() : null)
                .lat(snapshot != null ? snapshot.getLat() : null)
                .lng(snapshot != null ? snapshot.getLng() : null)
                .imageUrl(snapshot != null ? snapshot.getImage() : null)
                .build();
        return SavedPlaceDto.from(savedPlaceRepository.save(saved));
    }

    @Transactional
    public void remove(Long userId, String contentId) {
        savedPlaceRepository.deleteByUserIdAndContentId(userId, contentId);
    }

    /** tourism | food | cafe. 카페는 음식점 유형 안에 lclsSystm2=FD05로 들어 있다. */
    private String resolveKind(String contentTypeId, TourPlaceDto snapshot, String kindHint) {
        String normalizedHint = normalizeKind(kindHint);
        if (normalizedHint != null) {
            return normalizedHint;
        }
        if (!TourApiClient.FOOD_CONTENT_TYPE.equals(contentTypeId)) {
            return "tourism";
        }
        String foodCategory = snapshot != null ? snapshot.getFoodCategory() : null;
        return TourApiClient.CAFE_CATEGORIES.contains(foodCategory) ? "cafe" : "food";
    }

    private String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        return switch (kind.trim().toLowerCase()) {
            case "tour", "tourism" -> "tourism";
            case "food", "restaurant" -> "food";
            case "cafe" -> "cafe";
            default -> null;
        };
    }

    private Map<String, String> foodCategoriesFor(List<SavedPlace> rows) {
        Set<String> foodContentIds = rows.stream()
                .filter(row -> TourApiClient.FOOD_CONTENT_TYPE.equals(row.getContentTypeId()))
                .map(SavedPlace::getContentId)
                .collect(Collectors.toSet());
        if (foodContentIds.isEmpty()) {
            return Map.of();
        }
        try {
            return tourApiClient.findBusanFoodPlaces("ko").stream()
                    .filter(place -> foodContentIds.contains(place.getId()))
                    .filter(place -> place.getFoodCategory() != null)
                    .collect(Collectors.toMap(TourPlaceDto::getId, TourPlaceDto::getFoodCategory, (left, right) -> left));
        } catch (RuntimeException ex) {
            return Map.of();
        }
    }

    private String displayKind(SavedPlace row, Map<String, String> foodCategories) {
        String normalized = normalizeKind(row.getKind());
        if (TourApiClient.FOOD_CONTENT_TYPE.equals(row.getContentTypeId())
                && TourApiClient.CAFE_CATEGORIES.contains(foodCategories.get(row.getContentId()))) {
            return "cafe";
        }
        if (normalized != null) {
            return normalized;
        }
        return TourApiClient.FOOD_CONTENT_TYPE.equals(row.getContentTypeId()) ? "food" : "tourism";
    }

    private SavedPlaceDto localizedDto(SavedPlace row, String kind, String lang) {
        String title = tourPlaceI18n.translateTitle(lang, row.getContentId(), row.getTitle());
        String addr = tourPlaceI18n.translateAddress(lang, row.getContentId(), row.getAddr());
        return SavedPlaceDto.from(row, kind, title, addr);
    }

    private String currentLanguage() {
        String lang = LocaleContextHolder.getLocale().getLanguage();
        return lang == null || lang.isBlank() ? "ko" : lang.toLowerCase();
    }
}
