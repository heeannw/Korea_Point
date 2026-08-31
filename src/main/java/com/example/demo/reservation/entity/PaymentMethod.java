package com.example.demo.reservation.entity;

public enum PaymentMethod {
    CARD("신용·체크카드"),
    KAKAO_PAY("카카오페이"),
    NAVER_PAY("네이버페이");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
