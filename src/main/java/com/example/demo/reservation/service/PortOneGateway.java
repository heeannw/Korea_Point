package com.example.demo.reservation.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@Component
public class PortOneGateway {
    private final PortOneSettings settings;
    private final RestTemplate http;

    @Autowired
    public PortOneGateway(PortOneSettings settings) {
        this.settings = settings;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(60000);
        this.http = new RestTemplate(factory);
    }

    // Package-private constructor for HTTP contract tests; production host is fixed.
    PortOneGateway(PortOneSettings settings, RestTemplate http) {
        this.settings = settings;
        this.http = http;
    }

    public JsonNode lookup(String impUid) {
        if (impUid == null || !impUid.matches("imp_[a-zA-Z0-9_-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 식별자가 올바르지 않습니다.");
        }
        return request(HttpMethod.GET, "/payments/" + impUid + "?include_sandbox=true", null, token());
    }

    public void validate(JsonNode remote, String impUid, String merchantUid, int amount) {
        if (!impUid.equals(remote.path("imp_uid").asText())
                || !(merchantUid.equals(remote.path("merchant_uid").asText())
                    || PortOneOrderId.providerId(merchantUid).equals(remote.path("merchant_uid").asText()))
                || !remote.path("amount").isNumber()
                || remote.path("amount").decimalValue().compareTo(java.math.BigDecimal.valueOf(amount)) != 0
                || !"KRW".equals(remote.path("currency").asText())
                || !"tosspayments".equals(remote.path("pg_provider").asText())
                || !PortOneSettings.TEST_MID.equals(remote.path("pg_id").asText())
                || !("card".equals(remote.path("pay_method").asText())
                    || "point".equals(remote.path("pay_method").asText()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "예약 금액 또는 테스트 결제 정보가 일치하지 않습니다.");
        }
    }

    public JsonNode cancel(String impUid, String merchantUid, int amount) {
        var current = lookup(impUid);
        validate(current, impUid, merchantUid, amount);
        if ("cancelled".equals(current.path("status").asText()) && current.path("cancel_amount").asInt() == amount) return current;
        if (!"paid".equals(current.path("status").asText()) || current.path("cancel_amount").asInt() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "취소할 결제 상태를 확인해 주세요.");
        }
        request(HttpMethod.POST, "/payments/cancel", Map.of("imp_uid", impUid,
                "amount", amount, "checksum", amount, "reason", "KoreaPoint demo reservation cancellation"), token());
        var canceled = lookup(impUid);
        validate(canceled, impUid, merchantUid, amount);
        if (!"cancelled".equals(canceled.path("status").asText()) || canceled.path("cancel_amount").asInt() != amount) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 취소 결과를 아직 확인하지 못했습니다. 다시 확인해 주세요.");
        }
        return canceled;
    }

    private String token() {
        settings.requireReady();
        var response = request(HttpMethod.POST, "/users/getToken",
                Map.of("imp_key", settings.getApiKey(), "imp_secret", settings.getApiSecret()), null);
        String token = response.path("access_token").asText();
        if (token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 서비스 인증에 실패했습니다.");
        return token;
    }

    private JsonNode request(HttpMethod method, String path, Object body, String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.set("Authorization", token);
        try {
            var result = http.exchange("https://api.iamport.kr" + path, method,
                    new HttpEntity<>(body, headers), JsonNode.class).getBody();
            if (result == null || !result.path("code").isNumber() || result.path("code").asInt() != 0
                    || result.path("response").isMissingNode() || result.path("response").isNull()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 서비스에서 정상 응답을 받지 못했습니다.");
            }
            return result.get("response");
        } catch (RestClientException ex) {
            // Do not log provider responses, authorization headers or customer information.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제 결과를 확인하지 못했습니다. 잠시 후 다시 확인해 주세요.");
        }
    }
}
