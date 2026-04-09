package com.example.demo.trip.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_plan_day")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TripPlanDay {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_plan_id", nullable = false)
    private TripPlan tripPlan;

    @Column(nullable = false)
    private int day;

    @OneToMany(mappedBy = "tripPlanDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slotOrder ASC")
    @Builder.Default
    private List<TripPlanSlot> slots = new ArrayList<>();
}