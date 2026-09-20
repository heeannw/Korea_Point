package com.example.demo.tourism.service;

import com.example.demo.tourism.dto.PlaceReviewDto;
import com.example.demo.tourism.entity.PlaceReview;
import com.example.demo.tourism.entity.PlaceReviewTranslation;
import com.example.demo.tourism.repository.PlaceReviewRepository;
import com.example.demo.tourism.repository.PlaceReviewTranslationRepository;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 장소 방문 리뷰 CRUD. 한 사용자는 한 장소에 리뷰 하나(있으면 덮어쓴다). */
@Service
@RequiredArgsConstructor
public class PlaceReviewService {

    private final PlaceReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final PlaceReviewTranslationRepository translationRepository;

    /**
     * 한 장소의 리뷰를 보는 사람의 언어로 돌려준다.
     *
     * <p>리뷰는 쓴 사람의 언어 그대로 저장한다. 그 언어로 옮긴 문장이 있으면 그것을 담고,
     * 아직 없으면 원문을 담는다({@link PlaceReviewTranslationFiller} 가 곧 채운다).
     */
    @Transactional(readOnly = true)
    public List<PlaceReviewDto> list(String contentId, Long viewerUserId, String lang) {
        List<PlaceReview> reviews = reviewRepository.findByContentIdOrderByCreatedAtDesc(contentId);
        if (reviews.isEmpty()) return List.of();

        Map<Long, String> translated = translations(reviews, lang);
        return reviews.stream()
                .map(r -> PlaceReviewDto.of(r, viewerUserId, translated.get(r.getId())))
                .toList();
    }

    /** 리뷰 번호 -> 그 언어로 옮긴 문장. 옮길 필요가 없거나 아직 안 옮긴 리뷰는 담기지 않는다. */
    private Map<Long, String> translations(List<PlaceReview> reviews, String lang) {
        if (lang == null || lang.isBlank()) return Map.of();
        List<Long> ids = reviews.stream().map(PlaceReview::getId).toList();
        Map<Long, String> out = new HashMap<>();
        for (PlaceReviewTranslation t : translationRepository.findByLanguageAndReviewIdIn(lang, ids)) {
            if (t.getText() != null && !t.getText().isBlank()) out.put(t.getReviewId(), t.getText());
        }
        return out;
    }

    /** 별점(1~5)과 텍스트를 저장한다. 기존 리뷰가 있으면 수정. */
    @Transactional
    public PlaceReviewDto save(Long userId, String contentId, String contentTypeId, int rating, String text) {
        int clamped = Math.max(1, Math.min(5, rating));
        PlaceReview review = reviewRepository.findByUserIdAndContentId(userId, contentId)
                .orElseGet(() -> PlaceReview.builder()
                        .user(userRepository.getReferenceById(userId))
                        .contentId(contentId)
                        .contentTypeId(contentTypeId)
                        .build());
        review.setRating(clamped);
        review.setText(text == null || text.isBlank() ? null : text.trim());
        PlaceReview saved = reviewRepository.save(review);
        // 글이 바뀌었으니 옛 번역은 버린다. 보충 작업이 새 글로 다시 채운다.
        translationRepository.deleteByReviewId(saved.getId());
        return PlaceReviewDto.of(saved, userId, null);
    }

    /** 자기 리뷰만 삭제. */
    @Transactional
    public void delete(Long userId, Long reviewId) {
        reviewRepository.findById(reviewId)
                .filter(r -> r.getUser() != null && userId.equals(r.getUser().getId()))
                .ifPresent(r -> {
                    reviewRepository.delete(r);
                    translationRepository.deleteByReviewId(r.getId());
                });
    }
}
