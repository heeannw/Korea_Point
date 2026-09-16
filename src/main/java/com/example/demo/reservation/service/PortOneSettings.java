package com.example.demo.reservation.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "portone")
public class PortOneSettings {
    public static final String TEST_MID = "iamporttest_3";
    private boolean enabled;
    private boolean testChannelConfirmed;
    private String userCode = "";
    private String channelKey = "";
    private String apiKey = "";
    private String apiSecret = "";

    public boolean isReady() {
        return enabled && testChannelConfirmed && userCode.startsWith("imp")
                && !channelKey.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    public void requireReady() {
        if (!isReady()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "테스트 결제 설정이 완료되지 않았습니다.");
    }
}
