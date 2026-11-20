package com.tradingassistant.broker.angelone.http;

import com.tradingassistant.broker.BrokerTestFixtures;
import com.tradingassistant.broker.angelone.dto.AngelOneEnvelope;
import com.tradingassistant.broker.angelone.dto.QuoteResponseData;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerInvalidResponseException;
import com.tradingassistant.broker.exception.BrokerRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AngelOneHttpClientTest {

    private static final String QUOTE_PATH = "/rest/secure/angelbroking/market/v1/quote/";
    private static final ParameterizedTypeReference<AngelOneEnvelope<QuoteResponseData>> QUOTE_ENVELOPE =
            new ParameterizedTypeReference<>() {
            };

    private AngelOneProperties properties;
    private MockRestServiceServer server;
    private AngelOneHttpClient client;

    @BeforeEach
    void setUp() {
        properties = BrokerTestFixtures.enabledProperties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl().toString());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AngelOneHttpClient(properties, builder.build(), new ObjectMapper());
    }

    @Test
    void sendsCurrentAuthorizationAndBrokerHeaders() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-PrivateKey", "test-api-key"))
                .andExpect(header("X-UserType", "USER"))
                .andExpect(header("X-SourceID", "WEB"))
                .andExpect(header("Authorization", "Bearer test-jwt"))
                .andRespond(withSuccess("""
                        {"status":true,"message":"SUCCESS","errorcode":"","data":{"fetched":[],"unfetched":[]}}
                        """, MediaType.APPLICATION_JSON));

        AngelOneEnvelope<QuoteResponseData> response = client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP", "exchangeTokens", java.util.Map.of("NSE", java.util.List.of("3045"))),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.none());

        assertThat(response.status()).isTrue();
        assertThat(response.data().fetched()).isEmpty();
        server.verify();
    }

    @Test
    void retriesSafeServerFailuresWithBackoff() {
        server.expect(times(2), requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withSuccess("""
                        {"status":true,"message":"SUCCESS","errorcode":"","data":{"fetched":[],"unfetched":[]}}
                        """, MediaType.APPLICATION_JSON));

        AngelOneEnvelope<QuoteResponseData> response = client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.safe(properties));

        assertThat(response.status()).isTrue();
        server.verify();
    }

    @Test
    void translatesRateLimits() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":false,"message":"Access denied","errorcode":"AG8000","data":null}
                                """));

        assertThatThrownBy(() -> client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.none()))
                .isInstanceOf(BrokerRateLimitException.class);
        server.verify();
    }

    @Test
    void translatesDocumented403RateLimitResponse() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":false,"message":"Access denied because of exceeding access rate","errorcode":"","data":null}
                                """));

        assertThatThrownBy(() -> client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.none()))
                .isInstanceOf(BrokerRateLimitException.class);
        server.verify();
    }

    @Test
    void translatesPlainText403RateLimitResponse() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Access denied because of exceeding access rate"));

        assertThatThrownBy(() -> client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.none()))
                .isInstanceOf(BrokerRateLimitException.class);
        server.verify();
    }

    @Test
    void retriesPlainText403RateLimitWithHistoryPolicy() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Access denied because of exceeding access rate"));
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withSuccess("""
                        {"status":true,"message":"SUCCESS","errorcode":"","data":{"fetched":[],"unfetched":[]}}
                        """, MediaType.APPLICATION_JSON));

        AngelOneEnvelope<QuoteResponseData> response = client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                new RetryPolicy(1, java.time.Duration.ofMillis(1), java.time.Duration.ofMillis(1)));

        assertThat(response.status()).isTrue();
        server.verify();
    }

    @Test
    void mapsTotpRejectionFromSuccessfulHttpEnvelope() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withSuccess("""
                        {"status":false,"message":"Invalid TOTP","errorcode":"AB1000","data":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.none()))
                .isInstanceOf(BrokerAuthenticationException.class)
                .hasMessageContaining("TOTP");
        server.verify();
    }

    @Test
    void rejectsMalformedResponses() {
        server.expect(requestTo("https://apiconnect.angelone.in" + QUOTE_PATH))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.post(
                QUOTE_PATH,
                java.util.Map.of("mode", "LTP"),
                QUOTE_ENVELOPE,
                "test-jwt",
                RetryPolicy.none()))
                .isInstanceOf(BrokerInvalidResponseException.class);
        server.verify();
    }
}
