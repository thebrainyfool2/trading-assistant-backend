package com.tradingassistant.platform.history;

import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventListener;
import com.tradingassistant.pipeline.event.OrderAcceptedEvent;
import com.tradingassistant.pipeline.event.OrderCancelledEvent;
import com.tradingassistant.pipeline.event.OrderCreatedEvent;
import com.tradingassistant.pipeline.event.OrderFilledEvent;
import com.tradingassistant.pipeline.event.OrderPartiallyFilledEvent;
import com.tradingassistant.pipeline.event.OrderRejectedEvent;
import com.tradingassistant.pipeline.event.OrderSubmittedEvent;
import com.tradingassistant.pipeline.event.OrderValidatedEvent;
import com.tradingassistant.pipeline.event.TradeCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Projects engine order and trade events into durable history.
 *
 * <p>The projection is read-only with respect to the engine: it observes
 * published events and never publishes, mutates or blocks order processing. A
 * failure is logged and does not propagate to the engine's publication.</p>
 */
@Component
public class ExecutionHistoryListener implements MarketEventListener {

    private static final Logger log = LoggerFactory.getLogger(ExecutionHistoryListener.class);

    private final OrderHistoryService orderHistory;
    private final TradeHistoryService tradeHistory;

    public ExecutionHistoryListener(OrderHistoryService orderHistory,
                                    TradeHistoryService tradeHistory) {
        this.orderHistory = orderHistory;
        this.tradeHistory = tradeHistory;
    }

    @Override
    public void onEvent(MarketEvent event) {
        try {
            switch (event) {
                case OrderCreatedEvent created -> orderHistory.apply(created.order(), null);
                case OrderValidatedEvent validated -> orderHistory.apply(validated.order(), null);
                case OrderSubmittedEvent submitted -> orderHistory.apply(submitted.order(), null);
                case OrderAcceptedEvent accepted -> orderHistory.apply(accepted.order(), null);
                case OrderFilledEvent filled -> orderHistory.apply(filled.order(), null);
                case OrderPartiallyFilledEvent partial ->
                        orderHistory.apply(partial.order(), null);
                case OrderCancelledEvent cancelled ->
                        orderHistory.apply(cancelled.order(), cancelled.reason());
                case OrderRejectedEvent rejected ->
                        orderHistory.apply(rejected.order(), rejected.reason());
                case TradeCompletedEvent completed -> recordTrade(completed);
                default -> {
                    // Other events do not affect order or trade history.
                }
            }
        } catch (RuntimeException failure) {
            log.warn("History projection failed for {}: {}", event.type(),
                    failure.toString());
        }
    }

    private void recordTrade(TradeCompletedEvent event) {
        orderHistory.findByEngineOrderId(event.lifecycle().orderId())
                .ifPresent(record -> tradeHistory.record(record.userId(),
                        event.lifecycle()));
    }
}
