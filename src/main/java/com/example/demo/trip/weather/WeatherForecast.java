package com.example.demo.trip.weather;

import java.time.LocalDateTime;

public record WeatherForecast(
        LocalDateTime forecastAt,
        double temperature,
        int rainProbability,
        String precipitationType,
        double precipitationMm,
        double windSpeed,
        String sky
) {
    public boolean rainy() {
        return rainProbability >= 50 || precipitationMm > 0 || !"NONE".equals(precipitationType);
    }

    public boolean severeOutdoorWeather() {
        return rainProbability >= 70 || precipitationMm >= 5 || windSpeed >= 9 || temperature >= 33 || temperature <= -8;
    }
}
