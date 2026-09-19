package com.example.demo.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 마이페이지 포인트 충전 1건. 결제는 예약과 같은 포트원(테스트 채널)을 그대로 쓴다. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "point_charge")
public class PointCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "point_charge_seq")
    @SequenceGenerator(name = "point_charge_seq", sequenceName = "point_charge_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    // 충전 금액(원) = 적립 포인트. 1원 = 1포인트.
    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, unique = true, length = 60)
    private String merchantUid;

    @Column(length = 60)
    private String impUid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PointChargeStatus status = PointChargeStatus.READY;

    private LocalDateTime paidAt;

    @Column(length = 50)
    private String approvalNo;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
