package com.tradingassistant.broker.angelone;

import com.tradingassistant.broker.angelone.auth.AngelOneAuthenticator;
import com.tradingassistant.broker.angelone.market.AngelOneMarketDataProvider;
import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.broker.api.MarketFeedListener;
import com.tradingassistant.broker.model.BrokerName;
import com.tradingassistant.broker.model.BrokerProfile;
import com.tradingassistant.broker.model.Candle;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.broker.model.InstrumentMaster;
import com.tradingassistant.broker.model.Quote;
import com.tradingassistant.broker.model.QuoteMode;
import com.tradingassistant.broker.exception.BrokerSessionExpiredException;
import com.tradingassistant.broker.model.SessionStatus;

import java.util.List;
import java.util.Map;

/**
 * Angel One implementation of the broker facade. Application code sees only the
 * broker interfaces and token-free models.
 */
public final class AngelOneBrokerProvider implements BrokerProvider {

    private final AngelOneAuthenticator authenticator;
    private final AngelOneSessionManager sessionManager;
    private final AngelOneMarketDataProvider marketDataProvider;

    public AngelOneBrokerProvider(AngelOneAuthenticator authenticator,
                                  AngelOneSessionManager sessionManager,
                                  AngelOneMarketDataProvider marketDataProvider) {
        this.authenticator = authenticator;
        this.sessionManager = sessionManager;
        this.marketDataProvider = marketDataProvider;
    }

    @Override
    public BrokerName brokerName() {
        return BrokerName.ANGEL_ONE;
    }

    @Override
    public SessionStatus authenticate() {
        return sessionManager.authenticate();
    }

    @Override
    public BrokerProfile profile() {
        try {
            return authenticator.profile(sessionManager.accessToken());
        } catch (BrokerSessionExpiredException exception) {
            sessionManager.forceRenew();
            return authenticator.profile(sessionManager.accessToken());
        }
    }

    @Override
    public void logout() {
        sessionManager.logout();
    }

    @Override
    public SessionStatus sessionStatus() {
        return sessionManager.status();
    }

    @Override
    public SessionStatus ensureAuthenticated() {
        return sessionManager.ensureAuthenticated();
    }

    @Override
    public boolean isAuthenticated() {
        return sessionManager.isAuthenticated();
    }

    @Override
    public List<Quote> quotes(QuoteMode mode, Map<Exchange, List<String>> exchangeTokens) {
        return marketDataProvider.quotes(mode, exchangeTokens);
    }

    @Override
    public List<Candle> candles(CandleRequest request) {
        return marketDataProvider.candles(request);
    }

    @Override
    public InstrumentMaster instruments() {
        return marketDataProvider.instruments();
    }

    @Override
    public MarketFeedConnection openMarketFeed(FeedSubscription subscription, MarketFeedListener listener) {
        return marketDataProvider.openMarketFeed(subscription, listener);
    }
}
