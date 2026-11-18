package com.tradingassistant.broker.angelone.auth;

import com.tradingassistant.broker.BrokerTestFixtures;
import com.tradingassistant.broker.angelone.AngelOneSession;
import com.tradingassistant.broker.angelone.http.AngelOneHttpClient;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.model.BrokerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AngelOneAuthenticatorTest {

    private static final String BASE = "https://apiconnect.angelone.in";
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final AngelOneProperties properties = BrokerTestFixtures.enabledProperties();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private MockRestServiceServer server;
    private AngelOneAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        AngelOneHttpClient httpClient = new AngelOneHttpClient(properties, builder.build(), new ObjectMapper());
        authenticator = new AngelOneAuthenticator(
                httpClient,
                properties,
                new TotpCodeGenerator(BrokerTestFixtures.TEST_TOTP_SECRET),
                new JwtExpiryParser(new ObjectMapper()),
                clock);
    }

    @Test
    void loginUsesTheDocumentedRequestAndAppliesTheMidnightSessionLimit() {
        String token = jwt(2_000_000_000L);
        server.expect(requestTo(BASE + "/rest/auth/angelbroking/user/v1/loginByPassword"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.clientcode").value("test-client"))
                .andExpect(jsonPath("$.password").value("test-pin"))
                .andExpect(jsonPath("$.totp").isString())
                .andRespond(withSuccess(tokenEnvelope(token, "refresh", "feed"), MediaType.APPLICATION_JSON));

        AngelOneSession session = authenticator.login();

        assertThat(session.accessToken()).isEqualTo(token);
        assertThat(session.refreshToken()).isEqualTo("refresh");
        assertThat(session.feedToken()).isEqualTo("feed");
        assertThat(session.expiresAt()).isEqualTo(Instant.parse("2026-11-18T18:30:00Z"));
        server.verify();
    }

    @Test
    void refreshKeepsPreviousTokensWhenTheBrokerOmitsThem() {
        AngelOneSession previous = new AngelOneSession(
                "test-client", "old-jwt", "old-refresh", "old-feed", NOW, NOW.plusSeconds(3600));
        String token = jwt(2_000_000_000L);
        server.expect(requestTo(BASE + "/rest/auth/angelbroking/jwt/v1/generateTokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.refreshToken").value("old-refresh"))
                .andRespond(withSuccess(tokenEnvelope(token, null, null), MediaType.APPLICATION_JSON));

        AngelOneSession refreshed = authenticator.refresh(previous);

        assertThat(refreshed.accessToken()).isEqualTo(token);
        assertThat(refreshed.refreshToken()).isEqualTo("old-refresh");
        assertThat(refreshed.feedToken()).isEqualTo("old-feed");
        server.verify();
    }

    @Test
    void profileMapsDocumentedFields() {
        server.expect(requestTo(BASE + "/rest/secure/angelbroking/user/v1/getProfile"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":true,"message":"SUCCESS","errorcode":"","data":{
                          "clientcode":"test-client","name":"Test User","email":"test@example.com",
                          "mobileno":"9999999999","exchanges":["NSE"],"products":["CNC"],
                          "lastlogintime":"2026-11-18 15:30","brokerid":"test-broker"
                        }}
                        """, MediaType.APPLICATION_JSON));

        BrokerProfile profile = authenticator.profile("test-jwt");

        assertThat(profile.clientCode()).isEqualTo("test-client");
        assertThat(profile.name()).isEqualTo("Test User");
        assertThat(profile.brokerId()).isEqualTo("test-broker");
        server.verify();
    }

    @Test
    void logoutPostsTheClientCode() {
        AngelOneSession session = new AngelOneSession(
                "test-client", "test-jwt", "refresh", "feed", NOW, NOW.plusSeconds(3600));
        server.expect(requestTo(BASE + "/rest/secure/angelbroking/user/v1/logout"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.clientcode").value("test-client"))
                .andRespond(withSuccess("""
                        {"status":true,"message":"SUCCESS","errorcode":"","data":""}
                        """, MediaType.APPLICATION_JSON));

        authenticator.logout(session);

        server.verify();
    }

    @Test
    void rejectsAnExpiredJwtReturnedByTheBroker() {
        String token = jwt(1_700_000_000L);
        server.expect(requestTo(BASE + "/rest/auth/angelbroking/user/v1/loginByPassword"))
                .andRespond(withSuccess(tokenEnvelope(token, "refresh", "feed"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(authenticator::login)
                .isInstanceOf(BrokerAuthenticationException.class)
                .hasMessageContaining("expired");
        server.verify();
    }

    private static String tokenEnvelope(String token, String refreshToken, String feedToken) {
        return """
                {"status":true,"message":"SUCCESS","errorcode":"","data":{
                  "jwtToken":"%s","refreshToken":%s,"feedToken":%s
                }}
                """.formatted(
                token,
                refreshToken == null ? "null" : "\"" + refreshToken + "\"",
                feedToken == null ? "null" : "\"" + feedToken + "\"");
    }

    private static String jwt(long expiresAt) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"exp\":" + expiresAt + "}").getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJIUzUxMiJ9." + payload + ".signature";
    }
}
