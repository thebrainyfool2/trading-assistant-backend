package com.tradingassistant.market.service;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.exception.MarketDataException;
import com.tradingassistant.market.exception.MarketDataValidationException;
import com.tradingassistant.market.mapping.InstrumentMapper;
import com.tradingassistant.market.mapping.OptionContractMapper;

import java.util.List;
import java.util.Optional;

/**
 * Application-facing instrument access. There is intentionally no cache in
 * Phase 3; each call loads the provider's current instrument master.
 */
public final class InstrumentService {

    private final BrokerProvider broker;
    private final InstrumentMapper instrumentMapper;
    private final OptionContractMapper optionContractMapper;

    public InstrumentService(BrokerProvider broker,
                             InstrumentMapper instrumentMapper,
                             OptionContractMapper optionContractMapper) {
        this.broker = broker;
        this.instrumentMapper = instrumentMapper;
        this.optionContractMapper = optionContractMapper;
    }

    public List<Instrument> instruments() {
        try {
            return broker.instruments().instruments().stream()
                    .map(instrumentMapper::toDomain)
                    .toList();
        } catch (BrokerException exception) {
            throw MarketDataFailureMapper.translate(exception);
        }
    }

    public Optional<Instrument> find(InstrumentKey key) {
        if (key == null) {
            throw new MarketDataValidationException("instrument key must not be null");
        }
        return instruments().stream()
                .filter(instrument -> instrument.key().equals(key))
                .findFirst();
    }

    public List<OptionContract> optionContracts() {
        return instruments().stream()
                .filter(instrument -> instrument.type() == InstrumentType.OPTION)
                .map(optionContractMapper::fromInstrument)
                .flatMap(Optional::stream)
                .toList();
    }
}
