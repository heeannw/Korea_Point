package com.example.demo.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PortOneGatewayTest {
    @Test
    void legacyTruncatedOrderIsAcceptedButDifferentOrderIsRejected() {
        String legacy = "KPTEST-355e468f-6a7d-4615-9cf1-679dfc453415";
        var remote = payment().put("merchant_uid", legacy.substring(0, 40));
        gateway.validate(remote, "imp_test", legacy, 10000);
        remote.put("merchant_uid", legacy.substring(0, 39));
        assertThatThrownBy(() -> gateway.validate(remote, "imp_test", legacy, 10000))
                .isInstanceOf(ResponseStatusException.class);
        remote.put("merchant_uid", "KPTEST-455e468f-6a7d-4615-9cf1-679dfc453");
        assertThatThrownBy(() -> gateway.validate(remote, "imp_test", legacy, 10000))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void newOrderIdsFitProviderLimitWithoutLosingUuidEntropy() {
        String first = PortOneOrderId.create();
        assertThat(first).hasSize(39).matches("KPTEST-[a-f0-9]{32}");
        assertThat(PortOneOrderId.providerId(first)).isEqualTo(first);
        assertThat(PortOneOrderId.create()).isNotEqualTo(first);
    }
    @Test
    void springCreatesGatewayUsingProductionConstructor() {
        try (var context = new AnnotationConfigApplicationContext(PortOneSettings.class, PortOneGateway.class)) {
            assertThat(context.getBean(PortOneGateway.class)).isNotNull();
        }
    }

    private final PortOneSettings settings = new PortOneSettings();
    private final RestTemplate http = new RestTemplate();
    private final PortOneGateway gateway = new PortOneGateway(settings, http);

    @Test
    void disabledOrIncompleteConfigurationCannotCallProvider() {
        assertThatThrownBy(() -> gateway.lookup("imp_test")).isInstanceOf(ResponseStatusException.class);
        settings.setEnabled(true);
        assertThat(settings.isReady()).isFalse();
    }

    @Test
    void tossPointPaymentIsAcceptedOnlyForMatchingTestOrder() {
        var point = payment().put("pay_method", "point");
        gateway.validate(point, "imp_test", "KPTEST-order", 10000);
        point.put("pg_id", "live-mid");
        assertThatThrownBy(() -> gateway.validate(point, "imp_test", "KPTEST-order", 10000))
                .isInstanceOf(ResponseStatusException.class);
        point.put("pg_id", "iamporttest_3").put("amount", 9999);
        assertThatThrownBy(() -> gateway.validate(point, "imp_test", "KPTEST-order", 10000))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void lookupUsesV1TokenAndAuthenticatedApi() {
        configure();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("https://api.iamport.kr/users/getToken")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"imp_key\":\"key\",\"imp_secret\":\"secret\"}"))
                .andRespond(withSuccess("{\"code\":0,\"response\":{\"access_token\":\"token\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.iamport.kr/payments/imp_test?include_sandbox=true")).andExpect(header("Authorization", "token"))
                .andRespond(withSuccess("{\"code\":0,\"response\":" + payment() + "}", MediaType.APPLICATION_JSON));
        gateway.validate(gateway.lookup("imp_test"), "imp_test", "KPTEST-order", 10000);
        server.verify();
    }

    @Test
    void amountCurrencyOrderAndLiveMidAreRejected() {
        for (String field : new String[]{"currency", "merchant_uid", "imp_uid", "pg_provider", "pg_id", "pay_method"}) {
            var wrong = payment(); wrong.put(field, "wrong");
            assertThatThrownBy(() -> gateway.validate(wrong, "imp_test", "KPTEST-order", 10000))
                    .isInstanceOf(ResponseStatusException.class);
        }
        var wrong = payment(); wrong.put("amount", 10000.5);
        assertThatThrownBy(() -> gateway.validate(wrong, "imp_test", "KPTEST-order", 10000))
                .isInstanceOf(ResponseStatusException.class);
        gateway.validate(payment(), "imp_test", "KPTEST-order", 10000);
    }

    @Test
    void providerFailureDoesNotExposeSecretsOrRawBody() {
        configure();
        var server = MockRestServiceServer.bindTo(http).build();
        server.expect(requestTo("https://api.iamport.kr/users/getToken"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret customer details"));
        assertThatThrownBy(() -> gateway.lookup("imp_test")).isInstanceOf(ResponseStatusException.class)
                .hasMessageNotContaining("secret customer details");
    }

    private void configure() {
        settings.setEnabled(true); settings.setTestChannelConfirmed(true); settings.setUserCode("imp_demo");
        settings.setChannelKey("channel-demo"); settings.setApiKey("key"); settings.setApiSecret("secret");
    }

    @Test
    void cancellationChecksRemainingAmountAndConfirmsProviderRefund() {
        configure();
        var server = MockRestServiceServer.bindTo(http).build();
        var paid = payment().put("status", "paid").put("cancel_amount", 0);
        var canceled = payment().put("status", "cancelled").put("cancel_amount", 10000);
        tokenResponse(server);
        server.expect(requestTo("https://api.iamport.kr/payments/imp_test?include_sandbox=true"))
                .andRespond(withSuccess("{\"code\":0,\"response\":" + paid + "}", MediaType.APPLICATION_JSON));
        tokenResponse(server);
        server.expect(requestTo("https://api.iamport.kr/payments/cancel")).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"imp_uid\":\"imp_test\",\"amount\":10000,\"checksum\":10000}", false))
                .andRespond(withSuccess("{\"code\":0,\"response\":" + canceled + "}", MediaType.APPLICATION_JSON));
        tokenResponse(server);
        server.expect(requestTo("https://api.iamport.kr/payments/imp_test?include_sandbox=true"))
                .andRespond(withSuccess("{\"code\":0,\"response\":" + canceled + "}", MediaType.APPLICATION_JSON));
        assertThat(gateway.cancel("imp_test", "KPTEST-order", 10000).path("status").asText()).isEqualTo("cancelled");
        server.verify();
    }

    private void tokenResponse(MockRestServiceServer server) {
        server.expect(requestTo("https://api.iamport.kr/users/getToken"))
                .andRespond(withSuccess("{\"code\":0,\"response\":{\"access_token\":\"token\"}}", MediaType.APPLICATION_JSON));
    }
    private ObjectNode payment() {
        return new ObjectMapper().createObjectNode().put("imp_uid", "imp_test").put("merchant_uid", "KPTEST-order")
                .put("amount", 10000).put("currency", "KRW").put("pg_provider", "tosspayments")
                .put("pg_id", "iamporttest_3").put("pay_method", "card");
    }
}
