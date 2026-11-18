package com.tradingassistant.broker.config;

import com.tradingassistant.broker.BrokerTestFixtures;
import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.exception.BrokerConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BrokerConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void disabledBrokerDoesNotCreateAProvider() {
        runner.withBean(AngelOneProperties.class, BrokerTestFixtures::disabledProperties)
                .withPropertyValues("app.broker.angel-one.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(BrokerProvider.class));
    }

    @Test
    void enabledBrokerWithCompleteConfigurationCreatesOneProvider() {
        runner.withBean(AngelOneProperties.class, BrokerTestFixtures::enabledProperties)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(BrokerProvider.class);
                });
    }

    @Test
    void enabledBrokerWithoutSecretsFailsDuringStartup() {
        AngelOneProperties missingSecrets = new AngelOneProperties(
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

        runner.withBean(AngelOneProperties.class, () -> missingSecrets)
                .withPropertyValues("app.broker.angel-one.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BrokerConfigurationException.class);
                });
    }
}
