package com.example.demo.trip.weather;

public record HealthRisk(Integer uvIndex, String heatRisk, String coldRisk) {
    public boolean outdoorCaution() {
        return (uvIndex != null && uvIndex >= 8) || high(heatRisk) || high(coldRisk);
    }
    public String uvLevel() {
        if (uvIndex == null) return "정보 없음";
        if (uvIndex >= 11) return "위험";
        if (uvIndex >= 8) return "매우 높음";
        if (uvIndex >= 6) return "높음";
        if (uvIndex >= 3) return "보통";
        return "낮음";
    }
    private boolean high(String value) { return value != null && (value.contains("높") || value.contains("주의") || value.contains("경계") || value.contains("경고") || value.contains("위험") || value.contains("심각")); }
}
