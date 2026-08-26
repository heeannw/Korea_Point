package com.example.demo.tourism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tour-api")
public class TourApiProperties {

    /** 인증키는 TOUR_API_SERVICE_KEY 환경변수 또는 application-local.properties에서 주입한다. */
    private String serviceKey;
    private String koreanBaseUrl = "https://apis.data.go.kr/B551011/KorService2";
    private String englishBaseUrl = "https://apis.data.go.kr/B551011/EngService2";
    private String mobileOs = "ETC";
    private String mobileApp = "KoreaPoint";
    /**
     * 지역 필터는 법정동 시도코드(부산 26)를 쓴다.
     * areaCode는 신규 등록 항목에서 비어 오는 경우가 많아 해수욕장·축제가 통째로 누락된다.
     */
    private String busanLegalDongCode = "26";
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
    public String getBusanLegalDongCode() { return busanLegalDongCode; }
    public void setBusanLegalDongCode(String busanLegalDongCode) { this.busanLegalDongCode = busanLegalDongCode; }
    public int getDefaultRows() { return defaultRows; }
    public void setDefaultRows(int defaultRows) { this.defaultRows = defaultRows; }
}
