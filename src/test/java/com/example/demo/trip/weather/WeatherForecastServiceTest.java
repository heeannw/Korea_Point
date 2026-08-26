package com.example.demo.trip.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherForecastServiceTest {

    @Test
    void convertsBusanCoordinatesToKmaGrid() {
        WeatherForecastService.Grid grid = WeatherForecastService.toGrid(35.1796, 129.0756);

        assertThat(grid.x()).isEqualTo(98);
        assertThat(grid.y()).isEqualTo(76);
    }
}
