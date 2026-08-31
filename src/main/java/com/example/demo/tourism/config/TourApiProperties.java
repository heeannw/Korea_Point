package com.example.demo.tourism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "tour-api")
public class TourApiProperties {

    /** 인증키는 TOUR_API_SERVICE_KEY 환경변수 또는 application-local.properties에서 주입한다. */
    private String serviceKey;
    /**
     * 언어별 TourAPI 서비스 엔드포인트. 인증키는 공용이고 호스트 경로만 다르다.
     * 국문 외 서비스(Eng/Jpn/Chs/Fre)는 모두 영문과 같은 콘텐츠 유형 코드(76~85)를 쓴다.
     * 지원하지 않는 언어 코드는 국문으로 되돌린다({@code baseUrlFor}).
     */
    private String koreanBaseUrl = "https://apis.data.go.kr/B551011/KorService2";
    private String englishBaseUrl = "https://apis.data.go.kr/B551011/EngService2";
    private String japaneseBaseUrl = "https://apis.data.go.kr/B551011/JpnService2";
    /** 중국어 간체(Chs). 번체(Cht)가 필요해지면 별도 필드로 추가한다. */
    private String chineseBaseUrl = "https://apis.data.go.kr/B551011/ChsService2";
    private String frenchBaseUrl = "https://apis.data.go.kr/B551011/FreService2";
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
    public String getJapaneseBaseUrl() { return japaneseBaseUrl; }
    public void setJapaneseBaseUrl(String japaneseBaseUrl) { this.japaneseBaseUrl = japaneseBaseUrl; }
    public String getChineseBaseUrl() { return chineseBaseUrl; }
    public void setChineseBaseUrl(String chineseBaseUrl) { this.chineseBaseUrl = chineseBaseUrl; }
    public String getFrenchBaseUrl() { return frenchBaseUrl; }
    public void setFrenchBaseUrl(String frenchBaseUrl) { this.frenchBaseUrl = frenchBaseUrl; }

    /** 언어 코드로 서비스 엔드포인트를 고른다. 미지원 코드는 국문으로 되돌린다. */
    public String baseUrlFor(String lang) {
        if (lang == null) return koreanBaseUrl;
        return switch (lang.toLowerCase()) {
            case "en" -> englishBaseUrl;
            case "ja" -> japaneseBaseUrl;
            case "zh" -> chineseBaseUrl;
            case "fr" -> frenchBaseUrl;
            default -> koreanBaseUrl;
        };
    }

    public String getMobileOs() { return mobileOs; }
    public void setMobileOs(String mobileOs) { this.mobileOs = mobileOs; }
    public String getMobileApp() { return mobileApp; }
    public void setMobileApp(String mobileApp) { this.mobileApp = mobileApp; }
    public String getBusanLegalDongCode() { return busanLegalDongCode; }
    public void setBusanLegalDongCode(String busanLegalDongCode) { this.busanLegalDongCode = busanLegalDongCode; }
    public int getDefaultRows() { return defaultRows; }
    public void setDefaultRows(int defaultRows) { this.defaultRows = defaultRows; }
}
