package com.example.demo.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 부산광역시 스토리텔링 원형 정보 서비스 설정. 챗봇의 이야기 근거 자료로만 쓴다.
 * 원문은 DB에 저장하지 않고 필요할 때마다 이 API를 호출한다.
 */
@Component
@ConfigurationProperties(prefix = "chatbot.busan-story")
public class BusanStoryProperties {

    /** 인증키는 BUSAN_STORY_SERVICE_KEY 환경변수 또는 application-local.properties에서 주입한다. */
    private String serviceKey;
    private String baseUrl = "https://apis.data.go.kr/6260000/BusanCultureStoryTellingService/getBusanCultureStoryTelling";
    private int rowsPerPage = 100;
    /** 원문을 매번 API로 다시 받되, 짧은 시간은 메모리에 캐시해 호출 한도를 아낀다(DB 저장 아님). */
    private int cacheMinutes = 360;

    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public int getRowsPerPage() { return rowsPerPage; }
    public void setRowsPerPage(int rowsPerPage) { this.rowsPerPage = rowsPerPage; }
    public int getCacheMinutes() { return cacheMinutes; }
    public void setCacheMinutes(int cacheMinutes) { this.cacheMinutes = cacheMinutes; }
}
