package com.tradingassistant.options;

import com.tradingassistant.intelligence.IntelligenceTestFixtures;
import com.tradingassistant.intelligence.domain.AssessmentStatus;
import com.tradingassistant.intelligence.domain.Confidence;
import com.tradingassistant.intelligence.domain.ConfidenceLevel;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.domain.MomentumAssessment;
import com.tradingassistant.intelligence.domain.MomentumState;
import com.tradingassistant.intelligence.domain.ParticipationAssessment;
import com.tradingassistant.intelligence.domain.ParticipationState;
import com.tradingassistant.intelligence.domain.SessionAssessment;
import com.tradingassistant.intelligence.domain.SessionPhase;
import com.tradingassistant.intelligence.domain.StructureAssessment;
import com.tradingassistant.intelligence.domain.StructureBias;
import com.tradingassistant.intelligence.domain.StructureSignal;
import com.tradingassistant.intelligence.domain.TrendAssessment;
import com.tradingassistant.intelligence.domain.TrendState;
import com.tradingassistant.intelligence.domain.VolatilityAssessment;
import com.tradingassistant.intelligence.domain.VolatilityState;
import com.tradingassistant.market.domain.Currency;
import com.tradingassistant.market.domain.DepthLevel;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.MarketDepth;
import com.tradingassistant.market.domain.MarketStatus;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.options.chain.OptionChainBuilder;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.ChainSnapshot;
import com.tradingassistant.options.greeks.BlackScholesModel;
import com.tradingassistant.options.math.DecimalMath;
import com.tradingassistant.options.math.ExpiryTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OptionsTestFixtures {

    public static final InstrumentKey SPOT = new InstrumentKey(Segment.NSE_CASH, "3045");
    public static final InstrumentKey OTHER_SPOT = new InstrumentKey(Segment.NSE_CASH, "2885");
    public static final String UNDERLYING = "SBIN";
    public static final String OTHER_UNDERLYING = "INFY";
    public static final Instant START = IntelligenceTestFixtures.ist("2026-11-18T09:15:00");
    public static final LocalDate EXPIRY = LocalDate.of(2026, 11, 26);
    public static final LocalDate NEXT_EXPIRY = LocalDate.of(2026, 12, 31);
    public static final OptionsAnalyticsConfig CONFIG = OptionsAnalyticsConfig.builder().build();

    private OptionsTestFixtures() {
    }

    public static LocalDate expiryInDays(long days) {
        return START.atZone(OptionsAnalyticsConfig.DEFAULT.exchangeZone()).toLocalDate()
                .plusDays(days);
    }

    public static OptionContract contract(String underlying, LocalDate expiry, String strike,
                                          OptionType type) {
        return contract(underlying, expiry, strike, type, Segment.NSE_FO);
    }

    public static OptionContract contract(String underlying, LocalDate expiry, String strike,
                                          OptionType type, Segment segment) {
        BigDecimal strikeValue = new BigDecimal(strike);
        String token = underlying + "|" + expiry + "|" + strikeValue.toPlainString() + "|" + type;
        Instrument instrument = new Instrument(
                new InstrumentKey(segment, token),
                underlying + expiry + strikeValue.toPlainString() + type,
                underlying + expiry + strikeValue.toPlainString() + type,
                underlying + " option",
                InstrumentType.OPTION,
                new BigDecimal("0.05"),
                75L,
                null,
                null,
                Currency.INR,
                expiry,
                strikeValue,
                type,
                underlying);
        return new OptionContract(instrument, underlying, expiry, strikeValue, type);
    }

    public static List<OptionContract> ladder(String underlying, LocalDate expiry,
                                              List<String> strikes) {
        List<OptionContract> contracts = new ArrayList<>();
        for (String strike : strikes) {
            contracts.add(contract(underlying, expiry, strike, OptionType.CALL));
            contracts.add(contract(underlying, expiry, strike, OptionType.PUT));
        }
        return List.copyOf(contracts);
    }

    public static List<String> strikes(String from, String to, String step) {
        BigDecimal start = new BigDecimal(from);
        BigDecimal end = new BigDecimal(to);
        BigDecimal increment = new BigDecimal(step);
        List<String> values = new ArrayList<>();
        for (BigDecimal value = start; value.compareTo(end) <= 0; value = value.add(increment)) {
            values.add(value.toPlainString());
        }
        return List.copyOf(values);
    }

    /**
     * Theoretical premium at the given volatility, used to create quotes that
     * the implied-volatility solver can reproduce.
     */
    public static BigDecimal premium(OptionContract contract, String spot, String volatility,
                                     Instant at) {
        BigDecimal years = ExpiryTime.yearsToExpiry(contract.expiry(), at,
                CONFIG.exchangeZone(), CONFIG.dayCount());
        return new BlackScholesModel().price(contract.optionType(), new BigDecimal(spot),
                contract.strike(), CONFIG.riskFreeRate(), CONFIG.dividendYield(),
                new BigDecimal(volatility), years, CONFIG.dayCount());
    }

    public static Quote quote(OptionContract contract, String lastPrice, String netChange,
                              long openInterest, long volume, String bid, String ask) {
        BigDecimal bidPrice = new BigDecimal(bid);
        BigDecimal askPrice = new BigDecimal(ask);
        MarketDepth depth = new MarketDepth(
                List.of(new DepthLevel(bidPrice, 1_000L, 5L)),
                List.of(new DepthLevel(askPrice, 1_000L, 5L)));
        return new Quote(contract.instrument().key(),
                contract.instrument().tradingSymbol(),
                new BigDecimal(lastPrice), null, null, null, null, null,
                volume, openInterest, null,
                new BigDecimal(netChange), null, null, null,
                START, MarketStatus.OPEN, null, null, null, null, depth);
    }

    /**
     * Quotes every contract at its theoretical premium with a two-sided market.
     */
    public static Map<InstrumentKey, Quote> quoted(List<OptionContract> contracts, String spot,
                                                   String volatility, long openInterest,
                                                   long volume, String bid, String ask) {
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            BigDecimal premium = premium(contract, spot, volatility, START);
            quotes.put(contract.instrument().key(),
                    quote(contract, premium.toPlainString(), "0.50", openInterest, volume,
                            bid, ask));
        }
        return Map.copyOf(quotes);
    }

    public static Map<InstrumentKey, Quote> quotedAt(List<OptionContract> contracts, String spot,
                                                     String volatility, long openInterest,
                                                     long volume, String bid, String ask,
                                                     Instant at) {
        Map<InstrumentKey, Quote> quotes = new LinkedHashMap<>();
        for (OptionContract contract : contracts) {
            BigDecimal premium = premium(contract, spot, volatility, at);
            quotes.put(contract.instrument().key(),
                    new Quote(contract.instrument().key(),
                            contract.instrument().tradingSymbol(),
                            premium, null, null, null, null, null,
                            volume, openInterest, null, BigDecimal.ONE, null,
                            null, null, at, MarketStatus.OPEN, null, null,
                            null, null,
                            new MarketDepth(List.of(new DepthLevel(new BigDecimal(bid), 100L, 2L)),
                                    List.of(new DepthLevel(new BigDecimal(ask), 100L, 2L)))));
        }
        return Map.copyOf(quotes);
    }

    public static ChainSnapshot snapshot(List<OptionContract> contracts,
                                         Map<InstrumentKey, Quote> quotes,
                                         String spot) {
        return snapshot(contracts, quotes, spot, START);
    }

    public static ChainSnapshot snapshot(List<OptionContract> contracts,
                                         Map<InstrumentKey, Quote> quotes,
                                         String spot, Instant at) {
        return new OptionChainBuilder(CONFIG).build(SPOT, UNDERLYING, new BigDecimal(spot), at,
                contracts, quotes);
    }

    public static ChainSnapshot defaultSnapshot() {
        List<OptionContract> contracts = ladder(UNDERLYING, EXPIRY,
                strikes("460", "540", "10"));
        Map<InstrumentKey, Quote> quotes = quoted(contracts, "500", "0.25", 1_000L, 500L,
                "0.05", "0.05");
        return snapshot(contracts, quotes, "500");
    }

    public static MarketAssessment marketAssessment(Instant at) {
        return marketAssessment(SPOT, at);
    }

    public static MarketAssessment marketAssessment(InstrumentKey instrument, Instant at) {
        return new MarketAssessment(instrument, Interval.ONE_MINUTE, at,
                AssessmentStatus.READY, false,
                new Confidence(ConfidenceLevel.HIGH, "test assessment"),
                new TrendAssessment(TrendState.SIDEWAYS, AssessmentStatus.READY, Set.of(),
                        "test trend"),
                new MomentumAssessment(MomentumState.NEUTRAL, AssessmentStatus.READY, Set.of(),
                        "test momentum"),
                new VolatilityAssessment(VolatilityState.STABLE, AssessmentStatus.READY, Set.of(),
                        "test volatility"),
                new ParticipationAssessment(ParticipationState.AVERAGE, AssessmentStatus.READY,
                        Set.of(), "test participation"),
                new StructureAssessment(StructureBias.RANGING, Set.of(StructureSignal.RANGE),
                        AssessmentStatus.READY, "test structure"),
                new SessionAssessment(SessionPhase.REGULAR, AssessmentStatus.READY,
                        Optional.empty(), "test session"));
    }

    public static String plain(BigDecimal value) {
        return DecimalMath.format(value);
    }
}
