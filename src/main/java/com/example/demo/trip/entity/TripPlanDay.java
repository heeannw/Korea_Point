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

    /**
     * H2에서 "day"는 예약어라 컬럼명 충돌 발생
     * → @Column(name = "day_num") 으로 변경
     * Java 필드명은 그대로 day 유지 (setter/getter 변경 없음)
     */
    @Column(name = "day_num", nullable = false)
    private int day;

    @OneToMany(mappedBy = "tripPlanDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slotOrder ASC")
    @Builder.Default
    private List<TripPlanSlot> slots = new ArrayList<>();
}