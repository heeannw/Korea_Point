package com.example.demo.transport.service;

import com.example.demo.transport.dto.FastApiCongestionRequest;
import com.example.demo.transport.dto.FastApiCongestionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class CongestionMlService {

    @Value("${fastapi.base-url:http://127.0.0.1:8000}")
    private String fastapiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public int predict(int hour, int day, String station, int line) {
        try {
            String url = fastapiBaseUrl + "/predict";

            FastApiCongestionRequest request = new FastApiCongestionRequest(
                    hour,
                    day,
                    station,
                    line
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<FastApiCongestionRequest> entity = new HttpEntity<>(request, headers);
            ResponseEntity<FastApiCongestionResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    FastApiCongestionResponse.class
            );

            if (response.getBody() != null) {
                return response.getBody().getCongestion();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 1;
    }
}
