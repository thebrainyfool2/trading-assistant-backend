package com.tradingassistant.broker.angelone.market;

import com.tradingassistant.broker.angelone.dto.InstrumentDto;
import com.tradingassistant.broker.angelone.http.AngelOneHttpClient;
import com.tradingassistant.broker.angelone.http.RetryPolicy;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerInvalidResponseException;
import com.tradingassistant.broker.model.Instrument;
import com.tradingassistant.broker.model.InstrumentMaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads the public SmartAPI instrument master. The broker documents this as
 * a CSV dump, but the actual endpoint is a large JSON array whose keys differ
 * from the CSV column names; both naming variants are accepted by the DTO.
 */
final class AngelOneInstrumentMasterClient {

    private static final Logger log = LoggerFactory.getLogger(AngelOneInstrumentMasterClient.class);
    private static final ParameterizedTypeReference<List<InstrumentDto>> INSTRUMENT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final AngelOneHttpClient httpClient;
    private final AngelOneProperties properties;
    private final Clock clock;

    AngelOneInstrumentMasterClient(AngelOneHttpClient httpClient,
                                   AngelOneProperties properties,
                                   Clock clock) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.clock = clock;
    }

    InstrumentMaster load() {
        List<InstrumentDto> rows = httpClient.getAbsolute(
                properties.instrumentMasterUrl(), INSTRUMENT_LIST, RetryPolicy.safe(properties));
        if (rows == null || rows.isEmpty()) {
            throw new BrokerInvalidResponseException("The instrument master was empty.");
        }

        List<Instrument> instruments = new ArrayList<>(rows.size());
        int skipped = 0;
        for (InstrumentDto row : rows) {
            if (row == null || isBlank(row.token()) || isBlank(row.symbol())) {
                skipped++;
                continue;
            }
            instruments.add(new Instrument(
                    row.token().trim(),
                    row.symbol().trim(),
                    row.name(),
                    row.expiry(),
                    row.strike(),
                    row.tickSize(),
                    parseLong(row.lotSize()),
                    row.instrumentType(),
                    row.exchangeSegment(),
                    row.freezeQuantity()));
        }
        if (instruments.isEmpty()) {
            throw new BrokerInvalidResponseException("The instrument master contained no usable instruments.");
        }
        if (skipped > 0) {
            log.warn("broker_instrument_master_skipped count={}", skipped);
        }
        log.info("broker_instrument_master_loaded instruments={}", instruments.size());
        return new InstrumentMaster(instruments, clock.instant());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Long parseLong(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
