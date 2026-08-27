package com.example.demo.tourism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "busan-story")
public class BusanStoryProperties {

    /** 인증키는 BUSAN_STORY_SERVICE_KEY 환경변수 또는 application-local.properties에서 주입한다. */
    private String serviceKey;
    private String baseUrl = "https://apis.data.go.kr/6260000/BusanCultureStoryTellingService/getBusanCultureStoryTelling";
    private int rowsPerPage = 100;
    private int cacheMinutes = 360;
    /** 이 반경(미터) 안에 있는 스토리텔링 콘텐츠만 관광지 상세와 같은 장소로 본다. */
    private int matchRadiusMeters = 300;

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getRowsPerPage() { return rowsPerPage; }
    public void setRowsPerPage(int rowsPerPage) { this.rowsPerPage = rowsPerPage; }
    public int getCacheMinutes() { return cacheMinutes; }
    public void setCacheMinutes(int cacheMinutes) { this.cacheMinutes = cacheMinutes; }
    public int getMatchRadiusMeters() { return matchRadiusMeters; }
    public void setMatchRadiusMeters(int matchRadiusMeters) { this.matchRadiusMeters = matchRadiusMeters; }
}
