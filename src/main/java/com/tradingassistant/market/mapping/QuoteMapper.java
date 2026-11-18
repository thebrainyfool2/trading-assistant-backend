package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.exception.MarketDataMappingException;

import java.time.Instant;

/**
 * Normalizes a provider quote. The provider response has no quote timestamp, so
 * the application receipt instant is used and the caller supplies the session
 * status derived from that instant.
 */
public final class QuoteMapper {

    private final MarketDepthMapper depthMapper;

    public QuoteMapper(MarketDepthMapper depthMapper) {
        this.depthMapper = depthMapper;
    }

    public Quote toDomain(com.tradingassistant.broker.model.Quote source,
                          Instant receivedAt,
                          MarketStatus marketStatus) {
        if (source == null) {
            throw new MarketDataMappingException("A quote row was null.");
        }
        try {
            InstrumentKey key = new InstrumentKey(
                    ProviderExchangeMapping.segment(source.exchange()),
                    source.symbolToken());
            return new Quote(
                    key,
                    source.tradingSymbol(),
                    source.lastTradedPrice(),
                    source.open(),
                    source.high(),
                    source.low(),
                    source.close(),
                    source.averagePrice(),
                    source.tradeVolume(),
                    source.openInterest(),
                    source.lastTradeQuantity(),
                    source.netChange(),
                    source.percentChange(),
                    source.totalBuyQuantity(),
                    source.totalSellQuantity(),
                    receivedAt,
                    marketStatus,
                    source.upperCircuit(),
                    source.lowerCircuit(),
                    source.week52High(),
                    source.week52Low(),
                    depthMapper.toDomain(source.depth()));
        } catch (MarketDataMappingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MarketDataMappingException("A quote could not be mapped to the domain.", exception);
        }
    }
}
