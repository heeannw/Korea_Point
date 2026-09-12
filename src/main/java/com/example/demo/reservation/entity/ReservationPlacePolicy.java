package com.example.demo.reservation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_place_policy")
@Getter
@Setter
public class ReservationPlacePolicy {
    @Id
    @Column(length = 30)
    private String contentId;
    @Column(length = 30)
    private String contentTypeId;
    @Column(nullable = false, length = 500)
    private String placeTitle;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceFeeStatus feeStatus = PlaceFeeStatus.UNKNOWN;
    @Column(nullable = false)
    private boolean reservationEnabled;
    @Column(nullable = false, length = 1000)
    private String feeNote = "";
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @Version
    private Long version;

    public boolean isBookable() {
        return feeStatus == PlaceFeeStatus.PAID && reservationEnabled;
    }
}
