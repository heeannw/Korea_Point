package com.example.demo.trip.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trip_plan")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class TripPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String userId;          // OAuth sub or currentUserName

    @Column(nullable = false, length = 200)
    private String title;

    // 추천 파라미터 저장
    private int age;
    @Column(length = 20)  private String companion;
    @Column(length = 20)  private String budget;
    private int travelDays;
    private int startHour;
    private int endHour;
    @Column(length = 20)  private String intensity;
    @Column(length = 500) private String preferredAreas;
    @Column(length = 500) private String mustVisit;
    @Column(length = 300) private String categories;
    @Column(columnDefinition = "TEXT") private String styleDescription;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "tripPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("day ASC")
    @Builder.Default
    private List<TripPlanDay> days = new ArrayList<>();

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}