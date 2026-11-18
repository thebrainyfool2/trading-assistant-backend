package com.tradingassistant.broker.exception;

/**
 * Raised during startup when the broker integration is enabled but its required
 * configuration is missing or invalid. Messages identify environment variable
 * names only; configured values are never included.
 */
public final class BrokerConfigurationException extends BrokerException {

    public BrokerConfigurationException(String message) {
        super(BrokerError.CONFIGURATION, message);
    }

    public BrokerConfigurationException(String message, Throwable cause) {
        super(BrokerError.CONFIGURATION, message, cause);
    }
}
