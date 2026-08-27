package com.example.demo.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/** 부산광역시 스토리텔링 원형 정보 서비스의 이야기 한 건. 챗봇이 근거 자료로만 쓰고 DB엔 저장하지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusanStoryDto {

    // ✅ lat/lng가 JSON에서 문자열로 오기 때문에 커스텀 역직렬화가 필요하다.
    private static class StringToDouble extends StdDeserializer<Double> {
        public StringToDouble() { super(Double.class); }
        @Override
        public Double deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            String v = p.getText();
            if (v == null || v.isBlank()) return null;
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
        }
    }

    private String title;
    private String subtitle;
    private String contents;
    private String addr;
    private String age;
    private String imgUrl;

    @JsonDeserialize(using = StringToDouble.class)
    private Double lat;

    @JsonDeserialize(using = StringToDouble.class)
    private Double lng;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getContents() { return contents; }
    public void setContents(String contents) { this.contents = contents; }
    public String getAddr() { return addr; }
    public void setAddr(String addr) { this.addr = addr; }
    public String getAge() { return age; }
    public void setAge(String age) { this.age = age; }
    public String getImgUrl() { return imgUrl; }
    public void setImgUrl(String imgUrl) { this.imgUrl = imgUrl; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
}
