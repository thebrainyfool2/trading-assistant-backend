package com.tradingassistant.execution.broker;

import com.tradingassistant.execution.domain.ExecutionReport;
import com.tradingassistant.execution.domain.OrderExecution;
import com.tradingassistant.execution.domain.OrderStatus;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.exception.ExecutionBrokerException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic in-memory broker used in paper mode and in tests.
 *
 * <p>It fills a configurable fraction of the requested quantity at the limit price
 * or, for market orders, at the reference price. It can be told to reject
 * everything, to fail a number of submissions before succeeding, or to disconnect.
 * Nothing is persisted and no real market is simulated.</p>
 */
public final class PaperBrokerAdapter implements BrokerAdapter {

    public static final String NAME = "paper";
    private static final int MAX_REPORTS = 1024;

    private final String name;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, ExecutionReport> reports = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ExecutionReport> eldest) {
                    return size() > MAX_REPORTS;
                }
            });
    private final Map<String, Position> positions = new ConcurrentHashMap<>();
    private volatile BigDecimal fillRatio = BigDecimal.ONE;
    private volatile boolean rejectAll;
    private volatile boolean connected = true;
    private volatile int transientFailuresRemaining;

    public PaperBrokerAdapter(Clock clock) {
        this(NAME, clock);
    }

    public PaperBrokerAdapter(String name, Clock clock) {
        this.name = name;
        this.clock = clock;
    }

    @Override
    public String brokerName() {
        return name;
    }

    @Override
    public boolean connected() {
        return connected;
    }

    @Override
    public ExecutionReport placeOrder(BrokerOrderCommand command) {
        if (!connected) {
            throw new ExecutionBrokerException("paper broker is disconnected", true);
        }
        if (transientFailuresRemaining > 0) {
            transientFailuresRemaining--;
            throw new ExecutionBrokerException("paper broker is temporarily unavailable", true);
        }
        String brokerOrderId = name + "-" + sequence.incrementAndGet();
        if (rejectAll) {
            return store(new ExecutionReport(brokerOrderId, OrderStatus.REJECTED,
                    BigDecimal.ZERO, Optional.empty(), Optional.of("paper broker rejected"),
                    List.of(), clock.instant()));
        }
        BigDecimal requested = command.quantity();
        BigDecimal filled = requested.multiply(fillRatio)
                .setScale(4, RoundingMode.HALF_UP)
                .min(requested);
        if (filled.signum() <= 0) {
            return store(new ExecutionReport(brokerOrderId, OrderStatus.PENDING,
                    BigDecimal.ZERO, Optional.empty(), Optional.of("awaiting fill"),
                    List.of(), clock.instant()));
        }
        BigDecimal price = command.limitPrice().orElse(command.referencePrice());
        OrderExecution execution = new OrderExecution(brokerOrderId + "-fill",
                command.orderId(), filled, price, clock.instant());
        OrderStatus status = filled.compareTo(requested) == 0
                ? OrderStatus.FILLED
                : OrderStatus.PARTIALLY_FILLED;
        return store(new ExecutionReport(brokerOrderId, status, filled,
                Optional.of(price), Optional.of("paper fill"), List.of(execution),
                clock.instant()));
    }

    @Override
    public ExecutionReport cancelOrder(String brokerOrderId) {
        ExecutionReport existing = reports.get(brokerOrderId);
        ExecutionReport report = new ExecutionReport(brokerOrderId, OrderStatus.CANCELLED,
                existing == null ? BigDecimal.ZERO : existing.filledQuantity(),
                existing == null ? Optional.empty() : existing.averagePrice(),
                Optional.of("paper cancellation"), List.of(), clock.instant());
        return store(report);
    }

    @Override
    public ExecutionReport modifyOrder(BrokerOrderCommand command) {
        ExecutionReport existing = reports.get(command.orderId());
        String brokerOrderId = existing == null ? name + "-" + sequence.incrementAndGet()
                : existing.brokerOrderId();
        return store(new ExecutionReport(brokerOrderId, OrderStatus.PENDING,
                existing == null ? BigDecimal.ZERO : existing.filledQuantity(),
                existing == null ? Optional.empty() : existing.averagePrice(),
                Optional.of("paper modification accepted"), List.of(), clock.instant()));
    }

    @Override
    public Optional<ExecutionReport> orderStatus(String brokerOrderId) {
        return Optional.ofNullable(reports.get(brokerOrderId));
    }

    @Override
    public List<Position> positions() {
        return List.copyOf(new ArrayList<>(positions.values()));
    }

    /**
     * Fraction of each order that the paper broker fills.
     */
    public PaperBrokerAdapter fillRatio(BigDecimal ratio) {
        if (ratio == null || ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("fillRatio must be between 0 and 1");
        }
        this.fillRatio = ratio;
        return this;
    }

    public PaperBrokerAdapter rejectAll(boolean value) {
        this.rejectAll = value;
        return this;
    }

    public PaperBrokerAdapter connected(boolean value) {
        this.connected = value;
        return this;
    }

    /**
     * Makes the next submissions fail with a retryable broker error.
     */
    public PaperBrokerAdapter failNext(int attempts) {
        this.transientFailuresRemaining = Math.max(0, attempts);
        return this;
    }

    public PaperBrokerAdapter recordPosition(Position position) {
        positions.put(position.positionId(), position);
        return this;
    }

    /**
     * Reports known to the adapter, for verification.
     */
    public int reportCount() {
        return reports.size();
    }

    private ExecutionReport store(ExecutionReport report) {
        reports.put(report.brokerOrderId(), report);
        return report;
    }
}
