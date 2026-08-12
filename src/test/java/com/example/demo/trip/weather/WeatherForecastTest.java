package com.example.demo.trip.weather;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherForecastTest {

    @Test
    void detectsRainAndSevereOutdoorWeather() {
        WeatherForecast forecast = new WeatherForecast(
                LocalDateTime.of(2026, 8, 12, 14, 0), 34, 80, "RAIN", 6, 3, "OVERCAST");

        assertThat(forecast.rainy()).isTrue();
        assertThat(forecast.severeOutdoorWeather()).isTrue();
    }

    @Test
    void clearWeatherIsNotSevere() {
        WeatherForecast forecast = new WeatherForecast(
                LocalDateTime.of(2026, 8, 12, 10, 0), 25, 10, "NONE", 0, 2, "CLEAR");

        assertThat(forecast.rainy()).isFalse();
        assertThat(forecast.severeOutdoorWeather()).isFalse();
    }
}
