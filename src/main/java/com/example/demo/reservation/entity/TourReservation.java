package com.example.demo.reservation.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tour_reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TourReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 30)
    private String reservationNo;

    @Column(nullable = false, length = 100)
    private String contentId;

    @Column(length = 30)
    private String contentTypeId;

    @Column(nullable = false, length = 200)
    private String placeTitle;

    @Column(length = 10)
    @Builder.Default
    private String language = "ko";

    @Column(nullable = false)
    private LocalDate reservationDate;

    @Column(nullable = false, length = 10)
    private String reservationTime;

    @Column(nullable = false)
    private int peopleCount;

    @Column(length = 500)
    private String memo;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "결제 대기";

    @OneToOne(mappedBy = "reservation")
    @JsonIgnore
    private ReservationPayment payment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
