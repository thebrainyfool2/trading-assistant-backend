package com.tradingassistant.broker.config;

import com.tradingassistant.broker.exception.BrokerConfigurationException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Strongly typed SmartAPI configuration.
 *
 * <p>Secret values are supplied through environment variables and are never
 * written to configuration files. Validation runs only when the integration is
 * enabled, so the Phase 1 operational endpoints remain available in
 * environments that have not been given broker credentials.</p>
 */
@ConfigurationProperties(prefix = "app.broker.angel-one")
public record AngelOneProperties(
        boolean enabled,
        URI baseUrl,
        URI websocketUrl,
        URI instrumentMasterUrl,
        String apiKey,
        String clientCode,
        String pin,
        String totpSecret,
        String state,
        String clientLocalIp,
        String clientPublicIp,
        String macAddress,
        Duration connectTimeout,
        Duration readTimeout,
        Duration sessionExpirySkew,
        int maxRetries,
        Duration retryBaseDelay,
        Duration retryMaxDelay
) {

    private static final URI DEFAULT_BASE_URL = URI.create("https://apiconnect.angelone.in");
    private static final URI DEFAULT_WEBSOCKET_URL = URI.create("wss://smartapisocket.angelone.in/smart-stream");
    private static final URI DEFAULT_INSTRUMENT_MASTER_URL =
            URI.create("https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json");

    public AngelOneProperties {
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
        websocketUrl = websocketUrl == null ? DEFAULT_WEBSOCKET_URL : websocketUrl;
        instrumentMasterUrl = instrumentMasterUrl == null ? DEFAULT_INSTRUMENT_MASTER_URL : instrumentMasterUrl;
        state = state == null || state.isBlank() ? "prod" : state.trim();
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
        sessionExpirySkew = sessionExpirySkew == null ? Duration.ofSeconds(60) : sessionExpirySkew;
        retryBaseDelay = retryBaseDelay == null ? Duration.ofMillis(250) : retryBaseDelay;
        retryMaxDelay = retryMaxDelay == null ? Duration.ofSeconds(2) : retryMaxDelay;
    }

    /**
     * Fails fast when the adapter is enabled without a usable configuration.
     * Error messages contain variable names, never values.
     */
    public void validate() {
        if (!enabled) {
            return;
        }

        requireText(apiKey, "SMARTAPI_API_KEY");
        requireText(clientCode, "SMARTAPI_CLIENT_CODE");
        requireText(pin, "SMARTAPI_PIN");
        requireText(totpSecret, "SMARTAPI_TOTP_SECRET");
        requireText(clientLocalIp, "SMARTAPI_CLIENT_LOCAL_IP");
        requireText(clientPublicIp, "SMARTAPI_CLIENT_PUBLIC_IP");
        requireText(macAddress, "SMARTAPI_MAC_ADDRESS");
        requireHttps(baseUrl, "SMARTAPI_BASE_URL");
        requireWss(websocketUrl, "SMARTAPI_WEBSOCKET_URL");
        requireHttps(instrumentMasterUrl, "SMARTAPI_INSTRUMENT_MASTER_URL");
        requirePositive(connectTimeout, "SMARTAPI_CONNECT_TIMEOUT");
        requirePositive(readTimeout, "SMARTAPI_READ_TIMEOUT");
        requirePositive(sessionExpirySkew, "SMARTAPI_SESSION_EXPIRY_SKEW");
        requirePositive(retryBaseDelay, "SMARTAPI_RETRY_BASE_DELAY");
        requirePositive(retryMaxDelay, "SMARTAPI_RETRY_MAX_DELAY");
        if (maxRetries < 0 || maxRetries > 5) {
            throw new BrokerConfigurationException("SMARTAPI_MAX_RETRIES must be between 0 and 5");
        }
        if (retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            throw new BrokerConfigurationException("SMARTAPI_RETRY_MAX_DELAY must be greater than or equal to SMARTAPI_RETRY_BASE_DELAY");
        }
        if (sessionExpirySkew.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new BrokerConfigurationException("SMARTAPI_SESSION_EXPIRY_SKEW must not exceed 10 minutes");
        }
    }

    private static void requireText(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new BrokerConfigurationException(variableName + " is required when SMARTAPI_ENABLED is true");
        }
    }

    private static void requireHttps(URI uri, String variableName) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BrokerConfigurationException(variableName + " must be an https URI");
        }
    }

    private static void requireWss(URI uri, String variableName) {
        if (uri == null || !"wss".equalsIgnoreCase(uri.getScheme())) {
            throw new BrokerConfigurationException(variableName + " must be a wss URI");
        }
    }

    private static void requirePositive(Duration duration, String variableName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new BrokerConfigurationException(variableName + " must be a positive duration");
        }
    }
}
