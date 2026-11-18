package com.tradingassistant.broker.angelone.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpCodeGeneratorTest {

    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void generatesTheRfc6238Vector() {
        TotpCodeGenerator generator = new TotpCodeGenerator(RFC_SECRET, 8, Duration.ofSeconds(30));

        assertThat(generator.generate(Instant.ofEpochSecond(59))).isEqualTo("94287082");
    }

    @Test
    void acceptsWhitespaceInTheConfiguredSecret() {
        TotpCodeGenerator generator = new TotpCodeGenerator("GEZD GNBV GY3T QOJQ GEZD GNBV GY3T QOJQ", 8, Duration.ofSeconds(30));

        assertThat(generator.generate(Instant.ofEpochSecond(59))).isEqualTo("94287082");
    }

    @Test
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new TotpCodeGenerator(" "))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMARTAPI_TOTP_SECRET");
    }
}
