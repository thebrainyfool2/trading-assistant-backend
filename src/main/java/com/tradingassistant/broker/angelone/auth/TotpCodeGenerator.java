package com.tradingassistant.broker.angelone.auth;

import com.bastiaanjansen.otp.HMACAlgorithm;
import com.bastiaanjansen.otp.TOTPGenerator;
import com.tradingassistant.broker.exception.BrokerAuthenticationException;
import com.tradingassistant.broker.exception.BrokerConfigurationException;
import com.tradingassistant.broker.exception.BrokerError;

import java.time.Duration;
import java.time.Instant;

/**
 * Generates short-lived TOTP values for broker login. The generated value is
 * returned only to the login call and is never logged, cached or exposed.
 */
public final class TotpCodeGenerator {

    private static final int DEFAULT_DIGITS = 6;
    private static final Duration DEFAULT_PERIOD = Duration.ofSeconds(30);

    private final TOTPGenerator generator;

    public TotpCodeGenerator(String base32Secret) {
        this(base32Secret, DEFAULT_DIGITS, DEFAULT_PERIOD);
    }

    TotpCodeGenerator(String base32Secret, int digits, Duration period) {
        if (base32Secret == null || base32Secret.isBlank()) {
            throw new BrokerConfigurationException("SMARTAPI_TOTP_SECRET is required");
        }
        try {
            String normalizedSecret = base32Secret.replaceAll("\\s+", "");
            this.generator = new TOTPGenerator.Builder(normalizedSecret)
                    .withHOTPGenerator(builder -> builder
                            .withPasswordLength(digits)
                            .withAlgorithm(HMACAlgorithm.SHA1))
                    .withPeriod(period)
                    .build();
        } catch (RuntimeException exception) {
            throw new BrokerConfigurationException(
                    "SMARTAPI_TOTP_SECRET is not a valid Base32 TOTP secret", exception);
        }
    }

    public String generate(Instant instant) {
        try {
            return generator.at(instant);
        } catch (RuntimeException exception) {
            throw new BrokerAuthenticationException(
                    BrokerError.INVALID_TOTP, "TOTP generation failed.");
        }
    }
}
