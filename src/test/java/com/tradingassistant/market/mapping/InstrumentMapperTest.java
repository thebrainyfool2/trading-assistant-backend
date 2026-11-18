package com.tradingassistant.market.mapping;

import com.tradingassistant.market.MarketTestFixtures;
import com.tradingassistant.market.domain.Exchange;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.exception.MarketDataMappingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstrumentMapperTest {

    private final InstrumentMapper mapper = new InstrumentMapper();

    @Test
    void mapsEquityAndOptionRows() {
        Instrument equity = mapper.toDomain(MarketTestFixtures.providerEquity());
        assertThat(equity.exchange()).isEqualTo(Exchange.NSE);
        assertThat(equity.segment()).isEqualTo(Segment.NSE_CASH);
        assertThat(equity.type()).isEqualTo(InstrumentType.EQUITY);
        assertThat(equity.symbolToken()).isEqualTo("3045");
        assertThat(equity.lotSize()).isEqualTo(1L);
        assertThat(equity.displayName()).isEqualTo("SBIN");
        assertThat(equity.expiry()).isNull();

        Instrument option = mapper.toDomain(MarketTestFixtures.providerOption());
        assertThat(option.segment()).isEqualTo(Segment.NSE_FO);
        assertThat(option.type()).isEqualTo(InstrumentType.OPTION);
        assertThat(option.optionType()).isEqualTo(OptionType.CALL);
        assertThat(option.underlying()).isEqualTo("SBIN");
        assertThat(option.expiry()).isEqualTo(LocalDate.of(2024, 11, 28));
        assertThat(option.strike()).isEqualByComparingTo("2500.00");
    }

    @Test
    void mapsIndexSentinelValuesAndNcoSegment() {
        com.tradingassistant.broker.model.Instrument index =
                new com.tradingassistant.broker.model.Instrument(
                        "26074", "MIDCPNIFTY", "MIDCPNIFTY", "",
                        new BigDecimal("-1"), BigDecimal.ZERO, -1L, "", "NCO", BigDecimal.ZERO);

        Instrument instrument = mapper.toDomain(index);

        assertThat(instrument.segment()).isEqualTo(Segment.NSE_COM);
        assertThat(instrument.type()).isEqualTo(InstrumentType.OTHER);
        assertThat(instrument.tickSize()).isEqualByComparingTo("0");
        assertThat(instrument.lotSize()).isZero();
        assertThat(instrument.strike()).isNull();
    }

    @Test
    void mapsNcdexOptionSymbolWithSuffixAfterOptionType() {
        com.tradingassistant.broker.model.Instrument option =
                new com.tradingassistant.broker.model.Instrument(
                        "DHANIYA06APR27CE13900FAPR27",
                        "DHANIYA06APR27CE13900FAPR27",
                        "DHANIYA", "06APR2027", new BigDecimal("1390000"),
                        new BigDecimal("100"), 5L, "OPTFUT", "NCDEX", null);

        Instrument instrument = mapper.toDomain(option);

        assertThat(instrument.segment()).isEqualTo(Segment.NCDEX);
        assertThat(instrument.type()).isEqualTo(InstrumentType.OPTION);
        assertThat(instrument.optionType()).isEqualTo(OptionType.CALL);
        assertThat(instrument.underlying()).isEqualTo("DHANIYA");
        assertThat(instrument.expiry()).isEqualTo(LocalDate.of(2027, 4, 6));
    }

    @Test
    void supportsDocumentedExpiryFormats() {
        assertThat(InstrumentMapper.parseExpiry("28NOV2024")).isEqualTo(LocalDate.of(2024, 11, 28));
        assertThat(InstrumentMapper.parseExpiry("28-Nov-2024")).isEqualTo(LocalDate.of(2024, 11, 28));
        assertThat(InstrumentMapper.parseExpiry("2024-11-28")).isEqualTo(LocalDate.of(2024, 11, 28));
        assertThat(InstrumentMapper.parseExpiry("")).isNull();
    }

    @Test
    void rejectsUnknownSegmentMissingLotAndInvalidOptionSymbol() {
        com.tradingassistant.broker.model.Instrument unknownSegment =
                new com.tradingassistant.broker.model.Instrument(
                        "1", "X", "X", "", null, BigDecimal.ONE, 1L, "EQ", "UNKNOWN", null);
        assertThatThrownBy(() -> mapper.toDomain(unknownSegment))
                .isInstanceOf(MarketDataMappingException.class)
                .hasMessageContaining("segment");

        com.tradingassistant.broker.model.Instrument missingLot =
                new com.tradingassistant.broker.model.Instrument(
                        "1", "X", "X", "", null, BigDecimal.ONE, null, "EQ", "NSE", null);
        assertThatThrownBy(() -> mapper.toDomain(missingLot))
                .isInstanceOf(MarketDataMappingException.class)
                .hasMessageContaining("lot size");

        com.tradingassistant.broker.model.Instrument invalidOption =
                new com.tradingassistant.broker.model.Instrument(
                        "1", "X", "X", "28NOV24", BigDecimal.TEN, BigDecimal.ONE, 1L,
                        "OPTSTK", "NFO", null);
        assertThatThrownBy(() -> mapper.toDomain(invalidOption))
                .isInstanceOf(MarketDataMappingException.class)
                .hasMessageContaining("CALL or PUT");
    }

    @Test
    void rejectsUnsupportedExpiry() {
        com.tradingassistant.broker.model.Instrument invalid =
                new com.tradingassistant.broker.model.Instrument(
                        "1", "X28NOV24CE", "X", "not-a-date", BigDecimal.TEN, BigDecimal.ONE, 1L,
                        "OPTSTK", "NFO", null);

        assertThatThrownBy(() -> mapper.toDomain(invalid))
                .isInstanceOf(MarketDataMappingException.class)
                .hasMessageContaining("expiry");
    }
}
