package com.tradingassistant.platform.history;

import com.tradingassistant.execution.domain.TradeLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Persists completed trade lifecycle updates for the owning account.
 */
@Service
public class TradeHistoryService {

    private static final Logger log = LoggerFactory.getLogger(TradeHistoryService.class);

    private final TradeRecordRepository repository;
    private final Clock clock;

    public TradeHistoryService(TradeRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records a lifecycle update once. A redelivered event is ignored.
     */
    public void record(String userId, TradeLifecycle lifecycle) {
        if (userId == null || lifecycle == null) {
            return;
        }
        String key = TradeRecordDocument.key(lifecycle);
        if (repository.existsByTradeKey(key)) {
            return;
        }
        try {
            repository.save(TradeRecordDocument.from(userId, lifecycle,
                    clock.instant()));
        } catch (DuplicateKeyException duplicate) {
            log.debug("Trade history already contained {}", key);
        }
    }

    public Page<TradeRecordDocument> listForUser(String userId, int page, int size) {
        return repository.findByUserIdOrderByRecordedAtDesc(userId,
                PageRequest.of(Math.max(page, 0),
                        Math.min(Math.max(size, 1), OrderHistoryService.MAXIMUM_PAGE_SIZE)));
    }

    public Page<TradeRecordDocument> listAll(int page, int size) {
        return repository.findAllByOrderByRecordedAtDesc(
                PageRequest.of(Math.max(page, 0),
                        Math.min(Math.max(size, 1), OrderHistoryService.MAXIMUM_PAGE_SIZE)));
    }

    public long countForUser(String userId) {
        return repository.countByUserId(userId);
    }
}
