package com.example.demo.tourism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tour-api")
public class TourApiProperties {

    private String serviceKey = "22c5c4b72a66bb92fe5cff4db90cf9663bedd4f26be2879da38d3e166b1dd591";
    private String koreanBaseUrl = "https://apis.data.go.kr/B551011/KorService2";
    private String englishBaseUrl = "https://apis.data.go.kr/B551011/EngService2";
    private String mobileOs = "ETC";
    private String mobileApp = "KoreaPoint";
    private int busanAreaCode = 6;
    private int defaultRows = 1000;

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public String getKoreanBaseUrl() { return koreanBaseUrl; }
    public void setKoreanBaseUrl(String koreanBaseUrl) { this.koreanBaseUrl = koreanBaseUrl; }
    public String getEnglishBaseUrl() { return englishBaseUrl; }
    public void setEnglishBaseUrl(String englishBaseUrl) { this.englishBaseUrl = englishBaseUrl; }
    public String getMobileOs() { return mobileOs; }
    public void setMobileOs(String mobileOs) { this.mobileOs = mobileOs; }
    public String getMobileApp() { return mobileApp; }
    public void setMobileApp(String mobileApp) { this.mobileApp = mobileApp; }
    public int getBusanAreaCode() { return busanAreaCode; }
    public void setBusanAreaCode(int busanAreaCode) { this.busanAreaCode = busanAreaCode; }
    public int getDefaultRows() { return defaultRows; }
    public void setDefaultRows(int defaultRows) { this.defaultRows = defaultRows; }
}
