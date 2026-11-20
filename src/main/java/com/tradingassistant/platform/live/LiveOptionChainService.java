package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.mapping.OptionContractMapper;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.options.service.InMemoryOptionChainProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only staging of live option chains for monitored cash and index
 * instruments.
 *
 * <p>SmartAPI has no option-chain endpoint. The chain is assembled from the
 * instrument master and quote API: the nearest non-expired expiry is selected,
 * the strikes closest to spot are retained, and the resulting contracts and
 * quotes are handed to {@link InMemoryOptionChainProvider}. No analytics are
 * recalculated here and no order operation is available.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled", havingValue = "true")
public class LiveOptionChainService {

    private static final Logger log = LoggerFactory.getLogger(LiveOptionChainService.class);
    private static final int MAX_QUOTE_BATCH = 50;
    private static final Duration INSTRUMENT_CACHE_TTL = Duration.ofMinutes(10);
    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("Asia/Kolkata");

    private final MarketDataService marketData;
    private final InMemoryOptionChainProvider provider;
    private final Clock clock;
    private final boolean enabled;
    private final int strikesEachSide;
    private final long refreshIntervalMillis;
    private final int quoteBatchSize;
    private final OptionContractMapper contractMapper = new OptionContractMapper();
    private final Set<String> loggedProblems = ConcurrentHashMap.newKeySet();

    private volatile Universe universe;
    private volatile Instant lastRefresh = Instant.EPOCH;

    public LiveOptionChainService(MarketDataService marketData,
                                  InMemoryOptionChainProvider provider,
                                  Clock clock,
                                  @Value("${app.live.option-chain.enabled:true}") boolean enabled,
                                  @Value("${app.live.option-chain.strikes-each-side:10}")
                                  int strikesEachSide,
                                  @Value("${app.live.option-chain.refresh-interval-ms:30000}")
                                  long refreshIntervalMillis,
                                  @Value("${app.live.option-chain.quote-batch-size:50}")
                                  int quoteBatchSize) {
        this.marketData = marketData;
        this.provider = provider;
        this.clock = clock;
        this.enabled = enabled;
        this.strikesEachSide = Math.max(1, strikesEachSide);
        this.refreshIntervalMillis = Math.max(0, refreshIntervalMillis);
        this.quoteBatchSize = Math.max(1, Math.min(MAX_QUOTE_BATCH, quoteBatchSize));
    }

    /**
     * Refreshes chains for the supplied monitored instruments. Repeated calls
     * inside the configured interval are ignored so the 15-second live
     * reconciliation loop cannot spam the quote endpoint.
     */
    public void refresh(Set<InstrumentKey> monitored) {
        if (!enabled || monitored == null || monitored.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        if (Duration.between(lastRefresh, now).toMillis() < refreshIntervalMillis) {
            return;
        }
        synchronized (this) {
            now = clock.instant();
            if (Duration.between(lastRefresh, now).toMillis() < refreshIntervalMillis) {
                return;
            }
            lastRefresh = now;
            try {
                refreshNow(monitored);
            } catch (RuntimeException failure) {
                log.warn("Live option-chain refresh failed: {}", failure.toString());
            }
        }
    }

    private void refreshNow(Set<InstrumentKey> monitored) {
        Universe current = universe();
        if (current.options().isEmpty()) {
            return;
        }
        for (InstrumentKey instrument : monitored) {
            refreshOne(instrument, current);
        }
    }

    private void refreshOne(InstrumentKey spotKey, Universe current) {
        Instrument spot = current.instruments().get(spotKey);
        if (spot == null || spot.type() == InstrumentType.OPTION
                || spot.type() == InstrumentType.FUTURE) {
            return;
        }
        Segment optionSegment = optionSegment(spot.segment());
        if (optionSegment == null) {
            return;
        }

        String underlying = matchingUnderlying(spot, current.options(), optionSegment);
        if (underlying == null) {
            logOnce("No option contracts found for " + spotKey);
            return;
        }

        List<OptionContract> contracts = current.options().stream()
                .filter(contract -> contract.segment() == optionSegment)
                .filter(contract -> contract.underlying().equalsIgnoreCase(underlying))
                .toList();
        LocalDate today = clock.instant().atZone(EXCHANGE_ZONE).toLocalDate();
        LocalDate expiry = contracts.stream()
                .map(OptionContract::expiry)
                .filter(date -> !date.isBefore(today))
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (expiry == null) {
            logOnce("No unexpired option expiry found for " + spotKey);
            return;
        }

        BigDecimal spotPrice = spotPrice(spotKey);
        if (spotPrice == null) {
            return;
        }

        List<OptionContract> expiryContracts = contracts.stream()
                .filter(contract -> contract.expiry().equals(expiry))
                .toList();
        Set<BigDecimal> selectedStrikes = closestStrikes(expiryContracts, spotPrice);
        Map<String, OptionContract> unique = new LinkedHashMap<>();
        for (OptionContract contract : expiryContracts) {
            if (selectedStrikes.stream()
                    .anyMatch(strike -> strike.compareTo(contract.strike()) == 0)) {
                unique.putIfAbsent(contract.expiry() + "|" + canonical(contract.strike())
                        + "|" + contract.optionType(), contract);
            }
        }
        List<OptionContract> selected = List.copyOf(unique.values());
        if (selected.isEmpty()) {
            logOnce("No selectable option strikes found for " + spotKey);
            return;
        }

        List<Quote> quotes = optionQuotes(selected);
        if (quotes.isEmpty()) {
            logOnce("No option quotes returned for " + spotKey);
            return;
        }

        provider.registerUnderlying(spotKey, underlying);
        provider.updateContracts(underlying, selected);
        provider.updateQuotes(quotes);
        provider.updateSpot(spotKey, spotPrice);
        logOnce("Live option chain staged for " + spotKey + " " + underlying
                + " " + expiry + " strikes=" + selectedStrikes.size()
                + " quotes=" + quotes.size());
    }

    private BigDecimal spotPrice(InstrumentKey spotKey) {
        try {
            return marketData.quotes(QuoteMode.LTP, List.of(spotKey)).stream()
                    .filter(quote -> quote.instrument().equals(spotKey))
                    .map(Quote::lastTradedPrice)
                    .filter(price -> price != null && price.signum() > 0)
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException failure) {
            logOnce("Could not read spot quote for " + spotKey + ": " + failure.getMessage());
            return null;
        }
    }

    private List<Quote> optionQuotes(List<OptionContract> contracts) {
        List<Quote> quotes = new ArrayList<>();
        for (int start = 0; start < contracts.size(); start += quoteBatchSize) {
            int end = Math.min(start + quoteBatchSize, contracts.size());
            List<InstrumentKey> batch = contracts.subList(start, end).stream()
                    .map(contract -> contract.instrument().key())
                    .toList();
            try {
                quotes.addAll(marketData.quotes(QuoteMode.FULL, batch));
            } catch (RuntimeException failure) {
                logOnce("Option quote batch failed: " + failure.getMessage());
            }
        }
        return List.copyOf(quotes);
    }

    private Set<BigDecimal> closestStrikes(List<OptionContract> contracts, BigDecimal spot) {
        Set<BigDecimal> uniqueStrikes = new TreeSet<>();
        for (OptionContract contract : contracts) {
            uniqueStrikes.add(contract.strike());
        }
        List<BigDecimal> strikes = List.copyOf(uniqueStrikes);
        List<BigDecimal> nearest = strikes.stream()
                .sorted(Comparator.comparing((BigDecimal strike) -> strike.subtract(spot).abs())
                        .thenComparing(Comparator.naturalOrder()))
                .limit((long) strikesEachSide * 2 + 1)
                .toList();
        return new TreeSet<>(nearest);
    }

    private static String matchingUnderlying(Instrument spot, List<OptionContract> options,
                                             Segment optionSegment) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(spot.displayName());
        candidates.add(spot.symbol());
        candidates.add(stripCashSuffix(spot.symbol()));
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String normalized = candidate.trim().toUpperCase(Locale.ROOT);
            if (options.stream().anyMatch(contract -> contract.segment() == optionSegment
                    && contract.underlying().trim().equalsIgnoreCase(normalized))) {
                return normalized;
            }
        }
        return null;
    }

    private static String canonical(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static String stripCashSuffix(String symbol) {
        if (symbol == null) {
            return null;
        }
        return symbol.replaceFirst("-(EQ|BE|SM|ETF)$", "");
    }

    private static Segment optionSegment(Segment spot) {
        return switch (spot) {
            case NSE_CASH -> Segment.NSE_FO;
            case BSE_CASH -> Segment.BSE_FO;
            default -> null;
        };
    }

    private Universe universe() {
        Universe current = universe;
        if (current != null && current.validUntil().isAfter(clock.instant())) {
            return current;
        }
        List<Instrument> instruments = marketData.instruments();
        Map<InstrumentKey, Instrument> byKey = new LinkedHashMap<>();
        List<OptionContract> options = new ArrayList<>();
        int skipped = 0;
        for (Instrument instrument : instruments) {
            byKey.put(instrument.key(), instrument);
            if (instrument.type() != InstrumentType.OPTION) {
                continue;
            }
            try {
                contractMapper.fromInstrument(instrument).ifPresent(options::add);
            } catch (RuntimeException failure) {
                skipped++;
            }
        }
        Universe loaded = new Universe(Map.copyOf(byKey), List.copyOf(options),
                clock.instant().plus(INSTRUMENT_CACHE_TTL));
        universe = loaded;
        log.info("Live option universe loaded: instruments={} contracts={} skipped={}",
                byKey.size(), options.size(), skipped);
        return loaded;
    }

    private void logOnce(String message) {
        if (message != null && loggedProblems.add(message)) {
            log.info(message);
        }
    }

    private record Universe(Map<InstrumentKey, Instrument> instruments,
                            List<OptionContract> options,
                            Instant validUntil) {
    }
}
