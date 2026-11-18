package com.tradingassistant.broker.exception;

import java.time.Duration;

/**
 * The broker refused a request because its published rate limit was exceeded.
 * The optional retry delay comes from the broker's {@code Retry-After} header
 * when supplied.
 */
public final class BrokerRateLimitException extends BrokerException {

    private final Duration retryAfter;

    public BrokerRateLimitException(String message, Duration retryAfter, String brokerCode) {
        super(BrokerError.RATE_LIMITED, message, null, brokerCode);
        this.retryAfter = retryAfter;
    }

    public BrokerRateLimitException(String message, String brokerCode) {
        this(message, null, brokerCode);
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
