package com.tradingassistant.platform.live;

import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.market.service.InstrumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a configured token or a readable symbol alias to a provider token.
 *
 * <p>Numeric tokens are passed through without loading the instrument master.
 * Aliases are resolved from a TTL-bounded snapshot. For cash/index segments an
 * exact symbol, trading symbol or display name is accepted. For derivative
 * segments the nearest non-expired future is selected. The resolver never
 * guesses an option contract in this phase; option-chain selection belongs to
 * the options phase.</p>
 */
public final class LiveInstrumentResolver {

    private static final Logger log = LoggerFactory.getLogger(LiveInstrumentResolver.class);

    private final InstrumentService instruments;
    private final Clock clock;
    private final Duration cacheTtl;
    private final Object lock = new Object();

    private volatile Snapshot snapshot;

    public LiveInstrumentResolver(InstrumentService instruments, Clock clock, Duration cacheTtl) {
        this.instruments = Objects.requireNonNull(instruments, "instruments must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative()
                ? Duration.ofMinutes(10) : cacheTtl;
    }

    public Optional<InstrumentKey> resolve(String raw) {
        return resolve(LiveInstrumentSpec.parse(raw));
    }

    Optional<InstrumentKey> resolve(LiveInstrumentSpec spec) {
        if (spec == null) {
            return Optional.empty();
        }
        if (spec.numericToken()) {
            return Optional.of(new InstrumentKey(spec.segment(), spec.reference()));
        }
        Snapshot current = snapshot();
        if (current.instruments().isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        LocalDate today = now.atZone(java.time.ZoneId.of("Asia/Kolkata")).toLocalDate();
        return current.instruments().stream()
                .filter(instrument -> instrument.key().segment() == spec.segment())
                .filter(instrument -> instrument.type() != InstrumentType.OPTION)
                .filter(instrument -> instrument.type() != InstrumentType.FUTURE
                        || instrument.expiry() == null
                        || !instrument.expiry().isBefore(today))
                .filter(instrument -> matchesAlias(instrument, spec.reference()))
                .min(aliasComparator(spec.segment()))
                .map(Instrument::key);
    }

    private Snapshot snapshot() {
        Instant now = clock.instant();
        Snapshot current = snapshot;
        if (current != null && current.validUntil().isAfter(now)) {
            return current;
        }
        synchronized (lock) {
            current = snapshot;
            if (current != null && current.validUntil().isAfter(now)) {
                return current;
            }
            try {
                List<Instrument> loaded = List.copyOf(instruments.instruments());
                snapshot = new Snapshot(loaded, now.plus(cacheTtl));
                return snapshot;
            } catch (RuntimeException failure) {
                log.warn("Could not load the instrument master for live aliases: {}",
                        failure.toString());
                snapshot = new Snapshot(List.of(), now.plus(Duration.ofSeconds(30)));
                return snapshot;
            }
        }
    }

    private static boolean matchesAlias(Instrument instrument, String alias) {
        String normalized = alias.trim().toUpperCase(Locale.ROOT);
        if (equalsIgnoreCase(instrument.symbol(), normalized)
                || equalsIgnoreCase(instrument.tradingSymbol(), normalized)
                || equalsIgnoreCase(instrument.displayName(), normalized)) {
            return true;
        }
        if (instrument.type() == InstrumentType.FUTURE || instrument.type() == InstrumentType.INDEX) {
            String trading = instrument.tradingSymbol();
            String symbol = instrument.symbol();
            String display = instrument.displayName();
            return startsWithAlias(trading, normalized) || startsWithAlias(symbol, normalized)
                    || startsWithAlias(display, normalized);
        }
        return false;
    }

    private static boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.trim().equalsIgnoreCase(expected);
    }

    private static boolean startsWithAlias(String value, String alias) {
        return value != null && value.trim().toUpperCase(Locale.ROOT).startsWith(alias);
    }

    private static Comparator<Instrument> aliasComparator(Segment segment) {
        if (segment.isDerivative()) {
            return Comparator
                    .comparing((Instrument instrument) -> instrument.type() == InstrumentType.FUTURE ? 0 : 1)
                    .thenComparing(Instrument::expiry,
                            Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                    .thenComparing(instrument -> instrument.key().symbolToken());
        }
        return Comparator
                .comparing((Instrument instrument) -> typeRank(instrument.type()))
                .thenComparing(instrument -> instrument.key().symbolToken());
    }

    private static int typeRank(InstrumentType type) {
        return switch (type) {
            case INDEX -> 0;
            case EQUITY -> 1;
            case FUTURE -> 2;
            case OTHER -> 3;
            default -> 4;
        };
    }

    private record Snapshot(List<Instrument> instruments, Instant validUntil) {
    }
}
