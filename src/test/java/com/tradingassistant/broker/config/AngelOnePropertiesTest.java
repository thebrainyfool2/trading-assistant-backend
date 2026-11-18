package com.tradingassistant.broker.config;

import com.tradingassistant.broker.BrokerTestFixtures;
import com.tradingassistant.broker.exception.BrokerConfigurationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AngelOnePropertiesTest {

    @Test
    void disabledConfigurationDoesNotRequireSecrets() {
        assertThatCode(() -> BrokerTestFixtures.disabledProperties().validate())
                .doesNotThrowAnyException();
    }

    @Test
    void enabledConfigurationRequiresEveryCredential() {
        AngelOneProperties properties = new AngelOneProperties(
                true,
                URI.create("https://apiconnect.angelone.in"),
                URI.create("wss://smartapisocket.angelone.in/smart-stream"),
                URI.create("https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json"),
                null,
                null,
                null,
                null,
                "test",
                null,
                null,
                null,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(10));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BrokerConfigurationException.class)
                .hasMessageContaining("SMARTAPI_API_KEY")
                .hasMessageNotContaining("test-pin");
    }

    @Test
    void enabledConfigurationRejectsNonHttpsBaseUrl() {
        AngelOneProperties properties = new AngelOneProperties(
                true,
                URI.create("http://apiconnect.angelone.in"),
                URI.create("wss://smartapisocket.angelone.in/smart-stream"),
                URI.create("https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json"),
                "key",
                "client",
                "pin",
                BrokerTestFixtures.TEST_TOTP_SECRET,
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

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BrokerConfigurationException.class)
                .hasMessageContaining("SMARTAPI_BASE_URL");
    }

    @Test
    void enabledConfigurationRequiresClientRequestMetadata() {
        AngelOneProperties properties = new AngelOneProperties(
                true,
                URI.create("https://apiconnect.angelone.in"),
                URI.create("wss://smartapisocket.angelone.in/smart-stream"),
                URI.create("https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json"),
                "key",
                "client",
                "pin",
                BrokerTestFixtures.TEST_TOTP_SECRET,
                "test",
                null,
                null,
                null,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(60),
                2,
                Duration.ofMillis(1),
                Duration.ofMillis(10));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(BrokerConfigurationException.class)
                .hasMessageContaining("SMARTAPI_CLIENT_LOCAL_IP");
    }
}
