package com.tradingassistant.platform.history;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.execution.domain.ExecutionResult;
import com.tradingassistant.execution.domain.OrderIntent;
import com.tradingassistant.execution.domain.TradeOrder;
import com.tradingassistant.platform.error.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

/**
 * Owns durable order history.
 *
 * <p>History is written after the engine answers, never before: the engine is the
 * authority on what happened, and this service only remembers it. Subsequent
 * lifecycle events update the record by engine order identity.</p>
 */
@Service
public class OrderHistoryService {

    private static final Logger log = LoggerFactory.getLogger(OrderHistoryService.class);
    public static final int MAXIMUM_PAGE_SIZE = 100;

    private final OrderRecordRepository repository;
    private final Clock clock;

    public OrderHistoryService(OrderRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public OrderRecordDocument recordSubmission(String userId, OrderIntent intent,
                                                ExecutionResult result) {
        TradeOrder order = result.order().orElse(null);
        BigDecimal quantity = order == null ? BigDecimal.ZERO : order.quantity();
        String reason = result.reason().orElse(null);
        OrderRecordDocument document = OrderRecordDocument.from(userId,
                intent.strategyId(), intent.instrument().segment(),
                intent.instrument().symbolToken(), intent.side(), intent.type(),
                quantity, intent.limitPrice().orElse(null),
                intent.triggerPrice().orElse(null), intent.targetPrice().orElse(null),
                intent.timeInForce(), result.status(), order, reason, clock.instant());
        try {
            OrderRecordDocument saved = repository.save(document);
            if (result.position().isPresent()) {
                saved = repository.save(saved.withPosition(
                        result.position().get().positionId()));
            }
            return saved;
        } catch (RuntimeException failure) {
            log.error("Order history could not be recorded for user {}: {}", userId,
                    failure.toString());
            return document;
        }
    }

    /**
     * Applies a later lifecycle update. Unknown orders are ignored because the
     * record is written by the submitting account, not by the engine.
     */
    public void apply(TradeOrder order, String reason) {
        if (order == null) {
            return;
        }
        repository.findByEngineOrderId(order.orderId()).ifPresent(record -> {
            try {
                repository.save(record.withOrder(order, reason, clock.instant()));
            } catch (DuplicateKeyException failure) {
                log.debug("Order history update ignored for {}", order.orderId());
            }
        });
    }

    public Page<OrderRecordDocument> listForUser(String userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    public Page<OrderRecordDocument> listAll(int page, int size) {
        return repository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), clamp(size)));
    }

    public OrderRecordDocument requireForUser(String userId, String orderId) {
        return repository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new PlatformException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Order not found."));
    }

    public Optional<OrderRecordDocument> findForUser(String userId, String orderId) {
        return repository.findByIdAndUserId(orderId, userId);
    }

    public Optional<OrderRecordDocument> findByEngineOrderId(String engineOrderId) {
        return repository.findByEngineOrderId(engineOrderId);
    }

    public long countForUser(String userId) {
        return repository.countByUserId(userId);
    }

    private static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAXIMUM_PAGE_SIZE);
    }
}
