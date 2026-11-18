package com.tradingassistant.broker.api;

import com.tradingassistant.broker.model.BrokerName;

/**
 * The only broker entry point application services should depend on. A
 * provider exposes authentication, session and market-data capabilities without
 * exposing any broker-specific transport or token types.
 */
public interface BrokerProvider extends AuthenticationProvider, SessionProvider, MarketDataProvider {

    BrokerName brokerName();
}
