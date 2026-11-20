package com.tradingassistant.platform.instruments;

import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.service.InstrumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Cached, read-only view of the broker instrument master.
 *
 * <p>Search and premium monitoring must not download the whole SmartAPI
 * instrument master on every request. The catalog keeps one immutable snapshot
 * for a bounded window and exposes lookups over it. It never fetches quotes and
 * never mutates an instrument.</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class InstrumentCatalog {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final InstrumentService instruments;
    private final Clock clock;
    private final Duration ttl;
    private volatile Snapshot snapshot;

    @Autowired
    public InstrumentCatalog(InstrumentService instruments, Clock clock) {
        this(instruments, clock, DEFAULT_TTL);
    }

    InstrumentCatalog(InstrumentService instruments, Clock clock, Duration ttl) {
        this.instruments = instruments;
        this.clock = clock;
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative()
                ? DEFAULT_TTL : ttl;
    }

    /**
     * Every instrument in the current master snapshot.
     */
    public List<Instrument> all() {
        Snapshot current = snapshot;
        Instant now = clock.instant();
        if (current != null && current.validUntil().isAfter(now)) {
            return current.instruments();
        }
        synchronized (this) {
            current = snapshot;
            if (current != null && current.validUntil().isAfter(now)) {
                return current.instruments();
            }
            List<Instrument> loaded = List.copyOf(instruments.instruments());
            snapshot = new Snapshot(loaded, now.plus(ttl));
            return loaded;
        }
    }

    /**
     * Finds the listed option contract matching a manually recorded trade. The
     * exact trading symbol is preferred; the structural fields are a fallback
     * for providers whose symbol spellings differ.
     */
    public Optional<Instrument> findOption(InstrumentKey spot, String optionSymbol,
                                           BigDecimal strike, LocalDate expiry,
                                           OptionType optionType) {
        if (spot == null || strike == null || expiry == null || optionType == null) {
            return Optional.empty();
        }
        Segment expectedSegment = optionSegment(spot.segment());
        List<Instrument> options = all().stream()
                .filter(instrument -> instrument.type() == InstrumentType.OPTION)
                .filter(instrument -> instrument.expiry() != null
                        && instrument.expiry().equals(expiry))
                .filter(instrument -> instrument.strike() != null
                        && instrument.strike().compareTo(strike) == 0)
                .filter(instrument -> instrument.optionType() == optionType)
                .filter(instrument -> expectedSegment == null
                        || instrument.segment() == expectedSegment)
                .toList();
        if (options.isEmpty()) {
            return Optional.empty();
        }
        if (optionSymbol != null && !optionSymbol.isBlank()) {
            String normalized = optionSymbol.trim().toUpperCase(Locale.ROOT);
            Optional<Instrument> exact = options.stream()
                    .filter(instrument -> instrument.tradingSymbol()
                            .equalsIgnoreCase(normalized))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
        }
        String underlying = all().stream()
                .filter(instrument -> instrument.key().equals(spot))
                .findFirst()
                .map(InstrumentCatalog::underlyingName)
                .orElse(null);
        if (underlying != null) {
            Optional<Instrument> byUnderlying = options.stream()
                    .filter(instrument -> instrument.underlying() != null
                            && instrument.underlying().equalsIgnoreCase(underlying))
                    .findFirst();
            if (byUnderlying.isPresent()) {
                return byUnderlying;
            }
        }
        return options.stream().findFirst();
    }

    private static Segment optionSegment(Segment spot) {
        return switch (spot) {
            case NSE_CASH -> Segment.NSE_FO;
            case BSE_CASH -> Segment.BSE_FO;
            default -> null;
        };
    }

    private static String underlyingName(Instrument spot) {
        if (spot == null) {
            return null;
        }
        String candidate = spot.displayName();
        if (candidate == null || candidate.isBlank()) {
            candidate = spot.symbol();
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        return candidate.replaceFirst("-(EQ|BE|SM|ETF)$", "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private record Snapshot(List<Instrument> instruments, Instant validUntil) {
    }
}
