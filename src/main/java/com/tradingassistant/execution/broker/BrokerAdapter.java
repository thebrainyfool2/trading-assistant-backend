package com.tradingassistant.execution.broker;

import com.tradingassistant.execution.domain.ExecutionReport;
import com.tradingassistant.execution.domain.Position;
import com.tradingassistant.execution.exception.ExecutionBrokerException;

import java.util.List;
import java.util.Optional;

/**
 * Translate-and-transport contract for one broker.
 *
 * <p>An adapter does five things and nothing else: place, cancel, modify, report
 * status and report positions. It performs no validation, makes no risk decision,
 * keeps no portfolio and publishes no event. Any broker-specific mapping lives
 * behind this interface so the execution layer stays broker-independent.</p>
 */
public interface BrokerAdapter {

    /**
     * Stable name used for selection and diagnostics.
     */
    String brokerName();

    /**
     * Whether the adapter currently has a usable connection.
     */
    boolean connected();

    /**
     * Places an order.
     *
     * @throws ExecutionBrokerException when the broker cannot be reached; the
     *                                  exception states whether a retry is meaningful
     */
    ExecutionReport placeOrder(BrokerOrderCommand command);

    /**
     * Cancels an order previously accepted by the broker.
     */
    ExecutionReport cancelOrder(String brokerOrderId);

    /**
     * Modifies a working order.
     */
    ExecutionReport modifyOrder(BrokerOrderCommand command);

    /**
     * Reports the broker's view of an order, when it knows one.
     */
    Optional<ExecutionReport> orderStatus(String brokerOrderId);

    /**
     * Reports the broker's open positions.
     */
    List<Position> positions();
}
