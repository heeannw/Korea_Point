package com.example.demo.reservation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TourApiPlaceDto {

    private final String contentId;
    private final String contentTypeId;
    private final String title;
    private final String address;
    private final String tel;
    private final String imageUrl;
    private final String mapX;
    private final String mapY;
    private final String overview;

    public String getDisplayAddress() {
        return address == null || address.isBlank() ? "주소 정보 없음" : address;
    }

    public String getDisplayTel() {
        return tel == null || tel.isBlank() ? "전화번호 정보 없음" : tel;
    }
}
