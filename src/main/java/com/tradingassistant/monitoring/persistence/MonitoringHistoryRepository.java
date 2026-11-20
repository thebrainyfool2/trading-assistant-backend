package com.tradingassistant.monitoring.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** History repository; callers request a bounded newest-first page. */
public interface MonitoringHistoryRepository
        extends MongoRepository<MonitoringHistoryDocument, String> {

    List<MonitoringHistoryDocument> findByTradeIdOrderByTimestampDesc(String tradeId,
                                                                      Pageable pageable);

    List<MonitoringHistoryDocument> findAllByOrderByTimestampDesc(Pageable pageable);
}