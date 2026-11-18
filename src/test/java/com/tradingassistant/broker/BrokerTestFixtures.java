package com.tradingassistant.broker;

import com.tradingassistant.broker.config.AngelOneProperties;

import java.net.URI;
import java.time.Duration;

public final class BrokerTestFixtures {

    public static final String TEST_TOTP_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private BrokerTestFixtures() {
    }

    public static AngelOneProperties enabledProperties() {
        return new AngelOneProperties(
                true,
                URI.create("https://apiconnect.angelone.in"),
                URI.create("wss://smartapisocket.angelone.in/smart-stream"),
                URI.create("https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json"),
                "test-api-key",
                "test-client",
                "test-pin",
                TEST_TOTP_SECRET,
                "test",
                "127.0.0.1",
                "127.0.0.1",
                "00-00-00-00-00-00",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(10));
    }

    public static AngelOneProperties disabledProperties() {
        AngelOneProperties enabled = enabledProperties();
        return new AngelOneProperties(
                false,
                enabled.baseUrl(),
                enabled.websocketUrl(),
                enabled.instrumentMasterUrl(),
                null,
                null,
                null,
                null,
                enabled.state(),
                null,
                null,
                null,
                enabled.connectTimeout(),
                enabled.readTimeout(),
                enabled.sessionExpirySkew(),
                enabled.maxRetries(),
                enabled.retryBaseDelay(),
                enabled.retryMaxDelay());
    }
}
