package com.tradingassistant.broker.model;

/**
 * Account metadata returned by the broker. This object contains no tokens and
 * should not be logged wholesale because it contains personal information.
 */
public record BrokerProfile(
        String clientCode,
        String name,
        String email,
        String mobileNumber,
        String exchanges,
        String products,
        String lastLoginTime,
        String brokerId
) {
}
