package com.example.demo.trip.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "trip_plan_slot")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TripPlanSlot {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_plan_day_id", nullable = false)
    private TripPlanDay tripPlanDay;

    @Column(nullable = false) private int    slotOrder;
    @Column(length = 10)      private String time;
    @Column(length = 20)      private String category;
    @Column(nullable = false, length = 200) private String placeName;
    @Column(length = 500)     private String reason;
    private double score;
    private Double lat;
    private Double lng;
    @Column(length = 300)     private String address;
    @Column(length = 1000)    private String imageUrl;
    private double avgRating;
    private int reviewCount;
    private int durationMinutes;
}
