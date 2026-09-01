package com.example.demo.reservation.entity;

public enum PaymentStatus {
    READY("결제 대기"),
    PAID("예약금 결제 완료"),
    FAILED("결제 실패"),
    CANCELED("결제 취소"),
    REFUNDED("예약금 환불 완료"),
    FORFEITED("예약금 환불 불가");

    private final String label;

    PaymentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
