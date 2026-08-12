package com.example.demo.trip.weather;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HealthRiskTest {
    @Test void highUvRequiresOutdoorCaution() {
        HealthRisk risk = new HealthRisk(9, "", "");
        assertThat(risk.outdoorCaution()).isTrue();
        assertThat(risk.uvLevel()).isEqualTo("매우 높음");
    }

    @Test void warningImpactRequiresOutdoorCaution() {
        assertThat(new HealthRisk(2, "경고", "").outdoorCaution()).isTrue();
    }
}
