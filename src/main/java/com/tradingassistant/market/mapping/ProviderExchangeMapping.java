package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.exception.MarketDataMappingException;

/**
 * Boundary mapping for the Phase 2 provider's exchange enum. This is the only
 * place where the domain layer needs to know a provider exchange value.
 */
final class ProviderExchangeMapping {

    private ProviderExchangeMapping() {
    }

    static Segment segment(com.tradingassistant.broker.model.Exchange exchange) {
        if (exchange == null) {
            throw new MarketDataMappingException("The provider did not supply an exchange.");
        }
        return switch (exchange) {
            case NSE -> Segment.NSE_CASH;
            case NFO -> Segment.NSE_FO;
            case BSE -> Segment.BSE_CASH;
            case BFO -> Segment.BSE_FO;
            case MCX -> Segment.MCX_FO;
            case CDS -> Segment.CDS;
            case NCDEX -> Segment.NCDEX;
        };
    }
}
