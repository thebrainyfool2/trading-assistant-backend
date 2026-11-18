package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.Tick;
import com.tradingassistant.market.domain.TickMode;
import com.tradingassistant.market.exception.MarketDataMappingException;

import java.time.Instant;

/**
 * Converts a provider feed tick to the application's immutable event. The
 * application receipt timestamp is supplied by the feed adapter, not by the
 * provider packet.
 */
public final class TickMapper {

    private final MarketDepthMapper depthMapper;

    public TickMapper(MarketDepthMapper depthMapper) {
        this.depthMapper = depthMapper;
    }

    public Tick toDomain(com.tradingassistant.broker.model.FeedTick source,
                         Instant receivedAt,
                         MarketStatus marketStatus) {
        if (source == null) {
            throw new MarketDataMappingException("A market-feed tick was null.");
        }
        try {
            InstrumentKey key = new InstrumentKey(
                    ProviderExchangeMapping.segment(source.exchange()),
                    source.symbolToken());
            return new Tick(
                    tickMode(source.mode()),
                    key,
                    source.sequenceNumber(),
                    source.exchangeTimestamp(),
                    receivedAt,
                    source.lastTradedPrice(),
                    source.lastTradedQuantity(),
                    source.averageTradedPrice(),
                    source.tradeVolume(),
                    source.totalBuyQuantity(),
                    source.totalSellQuantity(),
                    source.open(),
                    source.high(),
                    source.low(),
                    source.close(),
                    source.lastTradedTimestamp(),
                    source.openInterest(),
                    depthMapper.toDomain(source.depth()),
                    source.upperCircuit(),
                    source.lowerCircuit(),
                    source.week52High(),
                    source.week52Low(),
                    marketStatus);
        } catch (MarketDataMappingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MarketDataMappingException("A market-feed tick could not be mapped to the domain.", exception);
        }
    }

    private static TickMode tickMode(com.tradingassistant.broker.model.FeedMode mode) {
        if (mode == null) {
            throw new MarketDataMappingException("A market-feed tick did not specify a mode.");
        }
        return switch (mode) {
            case LTP -> TickMode.LTP;
            case QUOTE -> TickMode.QUOTE;
            case SNAP_QUOTE -> TickMode.SNAPSHOT;
        };
    }
}
