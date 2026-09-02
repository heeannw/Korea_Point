package com.example.demo.tourism.dto;

import com.example.demo.tourism.entity.SavedPlace;

/** 저장(찜) 장소 목록 응답 한 건. */
public record SavedPlaceDto(
        Long id,
        String contentId,
        String contentTypeId,
        String kind,
        String title,
        String addr,
        Double lat,
        Double lng,
        String imageUrl
) {
    public static SavedPlaceDto from(SavedPlace s) {
        return from(s, s.getKind());
    }

    public static SavedPlaceDto from(SavedPlace s, String kind) {
        return new SavedPlaceDto(
                s.getId(), s.getContentId(), s.getContentTypeId(), kind,
                s.getTitle(), s.getAddr(), s.getLat(), s.getLng(), s.getImageUrl());
    }
}
