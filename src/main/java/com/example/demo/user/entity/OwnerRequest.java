package com.example.demo.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "owner_application")
public class OwnerRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "owner_application_seq")
    @SequenceGenerator(name = "owner_application_seq", sequenceName = "owner_application_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OwnerRequestStatus status = OwnerRequestStatus.PENDING;

    @Column(nullable = false, length = 120)
    private String storeName;

    @Column(nullable = false, length = 80)
    private String businessRegistrationNumber;

    /**
     * 국세청 진위확인에 쓴 대표자명. 관리자가 사업자등록증과 대조할 근거로 남긴다.
     * 진위확인이 생기기 전에 접수된 신청은 비어 있다.
     */
    @Column(length = 80)
    private String ownerName;

    /** 개업일자 여덟 자리(YYYYMMDD). 위와 같은 이유로 남긴다. */
    @Column(length = 8)
    private String startDate;

    @Column(length = 300)
    private String businessRegistrationFilePath;

    @Column(length = 300)
    private String businessLicenseFilePath;

    @Column(length = 300)
    private String menuFilePath;

    @Column(length = 500)
    private String reviewComment;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
