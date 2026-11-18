package com.tradingassistant.broker.api;

import com.tradingassistant.broker.model.Candle;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.broker.model.InstrumentMaster;
import com.tradingassistant.broker.model.Quote;
import com.tradingassistant.broker.model.QuoteMode;

import java.util.List;
import java.util.Map;

/**
 * Read-only market-data operations. Order placement and portfolio operations are
 * intentionally absent.
 */
public interface MarketDataProvider {

    List<Quote> quotes(QuoteMode mode, Map<Exchange, List<String>> exchangeTokens);

    List<Candle> candles(CandleRequest request);

    InstrumentMaster instruments();

    MarketFeedConnection openMarketFeed(FeedSubscription subscription, MarketFeedListener listener);
}
