package com.tradingassistant.platform.instruments;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Human-friendly search over the instrument master.
 *
 * <p>The default result set is cash equity and index instruments, so a search
 * for a company does not disappear under thousands of option contracts. An
 * explicit {@code type} can search options or futures. Results are deterministic:
 * exact matches first, then prefix, then contains, then alphabetical.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class InstrumentSearchService {

    public static final int MAX_LIMIT = 50;
    private static final Set<InstrumentType> DEFAULT_TYPES =
            Set.of(InstrumentType.EQUITY, InstrumentType.INDEX, InstrumentType.OTHER);

    private final InstrumentCatalog catalog;

    public InstrumentSearchService(InstrumentCatalog catalog) {
        this.catalog = catalog;
    }

    public List<Instrument> search(String query, Segment segment,
                                   InstrumentType type, int limit) {
        if (query == null || query.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "A non-blank search query is required.");
        }
        String needle = normalize(query);
        int boundedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return catalog.all().stream()
                .filter(instrument -> segment == null || instrument.segment() == segment)
                .filter(instrument -> type == null
                        ? DEFAULT_TYPES.contains(instrument.type())
                        : instrument.type() == type)
                .map(instrument -> new Match(instrument, score(instrument, needle)))
                .filter(match -> match.score() >= 0)
                .sorted(Comparator.comparingInt(Match::score)
                        .thenComparing(match -> normalize(match.instrument().displayName()))
                        .thenComparing(match -> normalize(match.instrument().symbol()))
                        .thenComparing(match -> match.instrument().segment().name()))
                .limit(boundedLimit)
                .map(Match::instrument)
                .toList();
    }

    private static int score(Instrument instrument, String needle) {
        String symbol = normalize(instrument.symbol());
        String tradingSymbol = normalize(instrument.tradingSymbol());
        String displayName = normalize(instrument.displayName());
        String underlying = normalize(instrument.underlying());
        if (symbol.equals(needle) || displayName.equals(needle)
                || tradingSymbol.equals(needle)) {
            return 0;
        }
        if (symbol.startsWith(needle) || displayName.startsWith(needle)) {
            return 1;
        }
        if (symbol.contains(needle) || displayName.contains(needle)
                || tradingSymbol.contains(needle)) {
            return 2;
        }
        if (!underlying.isBlank() && underlying.contains(needle)) {
            return 3;
        }
        return -1;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record Match(Instrument instrument, int score) {
    }
}
