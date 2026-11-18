package com.tradingassistant.broker.angelone.auth;

import com.tradingassistant.broker.angelone.AngelOneSession;
import com.tradingassistant.broker.angelone.dto.AngelOneEnvelope;
import com.tradingassistant.broker.angelone.dto.GenerateTokenRequest;
import com.tradingassistant.broker.angelone.dto.LoginRequest;
import com.tradingassistant.broker.angelone.dto.LogoutRequest;
import com.tradingassistant.broker.angelone.dto.ProfileData;
import com.tradingassistant.broker.angelone.dto.TokenData;
import com.tradingassistant.broker.angelone.http.AngelOneHttpClient;
import com.tradingassistant.broker.angelone.http.RetryPolicy;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerError;
import com.tradingassistant.broker.exception.BrokerInvalidResponseException;
import com.tradingassistant.broker.model.BrokerProfile;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Implements the documented SmartAPI login, token refresh, profile and logout
 * calls. The birth-time session rule is enforced locally: a session is never
 * considered valid beyond the next midnight in Asia/Kolkata, even if the JWT
 * claim extends further.
 */
public class AngelOneAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(AngelOneAuthenticator.class);

    static final String LOGIN_PATH = "/rest/auth/angelbroking/user/v1/loginByPassword";
    static final String GENERATE_TOKEN_PATH = "/rest/auth/angelbroking/jwt/v1/generateTokens";
    static final String PROFILE_PATH = "/rest/secure/angelbroking/user/v1/getProfile";
    static final String LOGOUT_PATH = "/rest/secure/angelbroking/user/v1/logout";

    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final ParameterizedTypeReference<AngelOneEnvelope<TokenData>> TOKEN_ENVELOPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<AngelOneEnvelope<ProfileData>> PROFILE_ENVELOPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<AngelOneEnvelope<Object>> LOGOUT_ENVELOPE =
            new ParameterizedTypeReference<>() {
            };

    private final AngelOneHttpClient httpClient;
    private final AngelOneProperties properties;
    private final TotpCodeGenerator totpCodeGenerator;
    private final JwtExpiryParser jwtExpiryParser;
    private final Clock clock;

    public AngelOneAuthenticator(AngelOneHttpClient httpClient,
                                 AngelOneProperties properties,
                                 TotpCodeGenerator totpCodeGenerator,
                                 JwtExpiryParser jwtExpiryParser,
                                 Clock clock) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.totpCodeGenerator = totpCodeGenerator;
        this.jwtExpiryParser = jwtExpiryParser;
        this.clock = clock;
    }

    public AngelOneSession login() {
        Instant now = clock.instant();
        LoginRequest request = new LoginRequest(
                properties.clientCode(),
                properties.pin(),
                totpCodeGenerator.generate(now),
                properties.state());
        AngelOneEnvelope<TokenData> response = httpClient.post(
                LOGIN_PATH, request, TOKEN_ENVELOPE, null, RetryPolicy.none());
        return sessionFrom(response.data(), null, now);
    }

    public AngelOneSession refresh(AngelOneSession current) {
        if (current == null || !current.canRefresh()) {
            throw new BrokerAuthenticationException(
                    BrokerError.SESSION_EXPIRED, "No refresh token is available.");
        }
        Instant now = clock.instant();
        AngelOneEnvelope<TokenData> response = httpClient.post(
                GENERATE_TOKEN_PATH,
                new GenerateTokenRequest(current.refreshToken()),
                TOKEN_ENVELOPE,
                current.accessToken(),
                RetryPolicy.none());
        return sessionFrom(response.data(), current, now);
    }

    public BrokerProfile profile(String accessToken) {
        AngelOneEnvelope<ProfileData> response = httpClient.get(
                PROFILE_PATH, PROFILE_ENVELOPE, accessToken, RetryPolicy.safe(properties));
        ProfileData data = response.data();
        if (data == null) {
            throw new BrokerInvalidResponseException("The broker profile response was empty.");
        }
        return new BrokerProfile(
                data.clientcode(),
                data.name(),
                data.email(),
                data.mobileno(),
                textOrArray(data.exchanges()),
                textOrArray(data.products()),
                data.lastlogintime(),
                data.brokerid());
    }

    private static String textOrArray(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isArray() ? value.toString() : value.asText();
    }

    public void logout(AngelOneSession session) {
        if (session == null) {
            return;
        }
        httpClient.post(
                LOGOUT_PATH,
                new LogoutRequest(properties.clientCode()),
                LOGOUT_ENVELOPE,
                session.accessToken(),
                RetryPolicy.none());
    }

    private AngelOneSession sessionFrom(TokenData data, AngelOneSession previous, Instant now) {
        if (data == null || data.jwtToken() == null || data.jwtToken().isBlank()) {
            throw new BrokerInvalidResponseException("The broker token response was incomplete.");
        }

        String refreshToken = firstNonBlank(data.refreshToken(), previous == null ? null : previous.refreshToken());
        String feedToken = firstNonBlank(data.feedToken(), previous == null ? null : previous.feedToken());
        Instant midnight = nextMidnightIst(now);
        Instant jwtExpiry = jwtExpiryParser.expiresAt(data.jwtToken()).orElse(null);
        Instant expiresAt = jwtExpiry == null || jwtExpiry.isAfter(midnight) ? midnight : jwtExpiry;

        if (!expiresAt.isAfter(now)) {
            throw new BrokerAuthenticationException(
                    BrokerError.SESSION_EXPIRED, "The broker returned an expired session.");
        }

        log.info("broker_session_issued expiresAt={} refreshable={} feedTokenPresent={}",
                expiresAt, refreshToken != null, feedToken != null);
        return new AngelOneSession(
                properties.clientCode(),
                data.jwtToken(),
                refreshToken,
                feedToken,
                now,
                expiresAt);
    }

    private Instant nextMidnightIst(Instant now) {
        return LocalDate.now(clock.withZone(MARKET_ZONE))
                .plusDays(1)
                .atStartOfDay(MARKET_ZONE)
                .toInstant();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
