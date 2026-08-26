package com.example.demo.config;

import com.example.demo.trip.integration.ExternalApiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TripExternalApiConfig {
    @Bean("tripExternalRestTemplate")
    RestTemplate tripExternalRestTemplate(
            @Value("${external-api.connect-timeout-ms:2500}") int connectTimeout,
            @Value("${external-api.read-timeout-ms:5000}") int readTimeout) {
        return ExternalApiClient.create(connectTimeout, readTimeout);
    }
}
