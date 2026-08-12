package com.example.demo.trip.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AirQualityTest {
    @Test
    void detectsBadAirFromFineDustGrade() {
        AirQuality air = new AirQuality(55, 39, 2, 3, "2026-08-12 15:00");
        assertThat(air.bad()).isTrue();
        assertThat(air.gradeLabel()).isEqualTo("나쁨");
    }

    @Test
    void reportsGoodAir() {
        AirQuality air = new AirQuality(20, 10, 1, 1, "2026-08-12 15:00");
        assertThat(air.bad()).isFalse();
        assertThat(air.gradeLabel()).isEqualTo("좋음");
    }
}
