package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Instrument;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionType;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.market.domain.QuoteMode;
import com.tradingassistant.market.service.MarketDataService;
import com.tradingassistant.market.time.MarketStatusResolver;
import com.tradingassistant.monitoring.domain.ManualTrade;
import com.tradingassistant.monitoring.service.MonitoringQueryService;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.instruments.InstrumentCatalog;
import com.tradingassistant.platform.instruments.InstrumentReferences;
import com.tradingassistant.platform.notification.NotificationService;
import com.tradingassistant.platform.notification.NotificationType;
import com.tradingassistant.platform.security.LocalPrincipalFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Premium-level monitoring for manual option trades.
 *
 * <p>The existing monitoring engine evaluates the underlying thesis. This
 * service adds the missing owner-facing price boundary: it resolves the listed
 * option contract, polls its LTP in bounded batches, compares the premium with
 * the recorded target and stop, and raises an in-app notification once when a
 * boundary is crossed. It never places, modifies or closes an order.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
public class OptionPremiumService {

    private static final Logger log = LoggerFactory.getLogger(OptionPremiumService.class);
    private static final int MAX_QUOTE_BATCH = 50;

    private final MonitoringQueryService queries;
    private final InstrumentCatalog catalog;
    private final MarketDataService marketData;
    private final MarketCalendarRegistry calendars;
    private final NotificationService notifications;
    private final TradePlanConfig config;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Map<String, OptionPremiumSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, PremiumStatus> lastStatuses = new ConcurrentHashMap<>();

    public OptionPremiumService(MonitoringQueryService queries,
                                InstrumentCatalog catalog,
                                MarketDataService marketData,
                                MarketCalendarRegistry calendars,
                                NotificationService notifications,
                                TradePlanConfig config,
                                Clock clock) {
        this.queries = queries;
        this.catalog = catalog;
        this.marketData = marketData;
        this.calendars = calendars;
        this.notifications = notifications;
        this.config = config;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.trade-plan.premium-refresh-ms:30000}",
            initialDelayString = "${app.trade-plan.premium-initial-delay-ms:15000}")
    public void scheduledRefresh() {
        refresh();
    }

    /**
     * Refreshes every active trade. The guard prevents overlapping scheduled
     * runs when a broker quote request is slow.
     */
    public void refresh() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<ManualTrade> active;
            try {
                active = queries.active().stream()
                        .map(com.tradingassistant.monitoring.domain.ActiveTradeMonitoring::trade)
                        .toList();
            } catch (RuntimeException failure) {
                log.warn("Could not load active trades for premium monitoring: {}",
                        failure.toString());
                return;
            }
            if (active.isEmpty()) {
                return;
            }
            List<Resolved> resolved = new ArrayList<>();
            for (ManualTrade trade : active) {
                resolve(trade).ifPresent(resolved::add);
            }
            Map<InstrumentKey, Quote> quotes = quotes(resolved.stream()
                    .map(Resolved::instrument)
                    .toList());
            for (Resolved value : resolved) {
                OptionPremiumSnapshot snapshot = evaluate(value.trade(),
                        value.instrument(), quotes.get(value.instrument().key()),
                        true);
                snapshots.put(value.trade().tradeId(), snapshot);
            }
        } finally {
            running.set(false);
        }
    }

    public List<OptionPremiumSnapshot> active() {
        List<ManualTrade> active;
        try {
            active = queries.active().stream()
                    .map(com.tradingassistant.monitoring.domain.ActiveTradeMonitoring::trade)
                    .toList();
        } catch (RuntimeException failure) {
            throw new PlatformException(ErrorCode.DATABASE_UNAVAILABLE,
                    "Active trades are not available.");
        }
        List<OptionPremiumSnapshot> values = new ArrayList<>();
        for (ManualTrade trade : active) {
            OptionPremiumSnapshot cached = snapshots.get(trade.tradeId());
            values.add(cached == null ? evaluate(trade) : cached);
        }
        return List.copyOf(values);
    }

    public OptionPremiumSnapshot get(String tradeId) {
        ManualTrade trade = queries.require(tradeId).trade();
        if (!trade.active()) {
            return evaluate(trade, null, null, false);
        }
        return evaluate(trade);
    }

    private OptionPremiumSnapshot evaluate(ManualTrade trade) {
        Optional<Resolved> resolved = resolve(trade);
        if (resolved.isEmpty()) {
            return evaluate(trade, null, null, false);
        }
        Map<InstrumentKey, Quote> quotes = quotes(
                List.of(resolved.get().instrument()));
        return evaluate(trade, resolved.get().instrument(),
                quotes.get(resolved.get().instrument().key()), true);
    }

    private OptionPremiumSnapshot evaluate(ManualTrade trade, Instrument instrument,
                                           Quote quote, boolean notify) {
        Instant now = clock.instant();
        boolean sessionOpen = sessionOpen(trade, now);
        Optional<BigDecimal> target = trade.targetPremium();
        Optional<BigDecimal> stop = trade.stopPremium();
        Optional<BigDecimal> premium = quote == null ? Optional.empty()
                : Optional.ofNullable(quote.lastTradedPrice());
        PremiumStatus status;
        if (!trade.active()) {
            status = PremiumStatus.CLOSED;
        } else if (trade.expiry().isBefore(now.atZone(
                MarketStatusResolver.MARKET_ZONE).toLocalDate())) {
            status = PremiumStatus.EXPIRED;
        } else if (premium.isEmpty()) {
            status = PremiumStatus.NO_QUOTE;
        } else if (target.isPresent()
                && premium.get().compareTo(target.get()) >= 0) {
            status = PremiumStatus.TARGET_HIT;
        } else if (stop.isPresent()
                && premium.get().compareTo(stop.get()) <= 0) {
            status = PremiumStatus.STOP_HIT;
        } else {
            status = PremiumStatus.HOLDING;
        }
        Optional<BigDecimal> pnl = premium.map(value -> value
                .subtract(trade.entryPrice())
                .multiply(trade.quantity())
                .setScale(2, RoundingMode.HALF_UP));
        Optional<BigDecimal> pnlPercent = premium.map(value -> value
                .subtract(trade.entryPrice())
                .divide(trade.entryPrice(), MathContext.DECIMAL128)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP));
        OptionPremiumSnapshot snapshot = new OptionPremiumSnapshot(
                trade.tradeId(),
                instrument == null ? "" : InstrumentReferences.format(instrument.key()),
                trade.optionSymbol(), trade.entryPrice(), premium, pnl, pnlPercent,
                target, stop, status, sessionOpen, now);
        if (notify) {
            notifyBoundary(trade, snapshot);
        }
        return snapshot;
    }

    private void notifyBoundary(ManualTrade trade, OptionPremiumSnapshot snapshot) {
        if (!config.premiumAlertsEnabled() || !snapshot.sessionOpen()) {
            return;
        }
        PremiumStatus prior = lastStatuses.put(trade.tradeId(), snapshot.status());
        if (prior == snapshot.status()) {
            return;
        }
        if (snapshot.status() == PremiumStatus.TARGET_HIT
                || snapshot.status() == PremiumStatus.STOP_HIT
                || snapshot.status() == PremiumStatus.EXPIRED) {
            String action = switch (snapshot.status()) {
                case TARGET_HIT -> "target reached";
                case STOP_HIT -> "stop reached";
                case EXPIRED -> "contract expired";
                default -> "premium boundary";
            };
            String premium = snapshot.premium()
                    .map(value -> value.toPlainString()).orElse("unavailable");
            notifications.notify(LocalPrincipalFilter.OWNER_ID,
                    NotificationType.TRADE_ALERT,
                    "Option trade " + action,
                    trade.optionSymbol() + " is " + premium
                            + " against entry " + trade.entryPrice().toPlainString()
                            + ". Review the position and exit manually.");
        }
    }

    private Optional<Resolved> resolve(ManualTrade trade) {
        OptionType type = trade.recommendationType()
                == com.tradingassistant.recommendation.domain.RecommendationType.BUY_CALL
                ? OptionType.CALL : OptionType.PUT;
        return catalog.findOption(trade.instrument(), trade.optionSymbol(),
                        trade.strike(), trade.expiry(), type)
                .map(instrument -> new Resolved(trade, instrument));
    }

    private Map<InstrumentKey, Quote> quotes(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        List<InstrumentKey> keys = instruments.stream()
                .map(Instrument::key)
                .distinct()
                .toList();
        Map<InstrumentKey, Quote> values = new LinkedHashMap<>();
        for (int start = 0; start < keys.size(); start += MAX_QUOTE_BATCH) {
            int end = Math.min(start + MAX_QUOTE_BATCH, keys.size());
            try {
                for (Quote quote : marketData.quotes(QuoteMode.LTP,
                        keys.subList(start, end))) {
                    values.put(quote.instrument(), quote);
                }
            } catch (RuntimeException failure) {
                log.warn("Option premium quote batch failed: {}", failure.toString());
            }
        }
        return Map.copyOf(values);
    }

    private boolean sessionOpen(ManualTrade trade, Instant now) {
        return calendars.find(trade.instrument().exchange())
                .map(calendar -> calendar.isTradingTime(now))
                .orElse(true);
    }

    private record Resolved(ManualTrade trade, Instrument instrument) {
    }
}
