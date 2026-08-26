package com.example.demo.trip.weather;

public record AirQuality(int pm10, int pm25, int pm10Grade, int pm25Grade, String dataTime) {
    public boolean bad() { return pm10Grade >= 3 || pm25Grade >= 3 || pm10 > 80 || pm25 > 35; }
    public String gradeLabel() {
        int grade = Math.max(pm10Grade, pm25Grade);
        return switch (grade) { case 1 -> "좋음"; case 2 -> "보통"; case 3 -> "나쁨"; case 4 -> "매우 나쁨"; default -> "정보 없음"; };
    }
}
