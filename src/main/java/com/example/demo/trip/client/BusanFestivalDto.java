package com.example.demo.trip.client;

import java.time.LocalDate;

/**
 * 여행 기간과 겹치는 부산 축제 하나. tourism 모듈의 TourPlaceDto는 이 시작일/종료일을
 * 밖으로 안 내보내서, trip 전용으로 TourAPI를 직접 호출해 필요한 것만 담는다.
 */
public record BusanFestivalDto(
        String title,
        LocalDate startDate,
        LocalDate endDate,
        double lat,
        double lng,
        String address,
        String imageUrl
) {}
