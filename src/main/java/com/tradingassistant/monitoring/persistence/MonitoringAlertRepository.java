package com.tradingassistant.monitoring.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Alert repository; callers request a bounded newest-first page. */
public interface MonitoringAlertRepository
        extends MongoRepository<MonitoringAlertDocument, String> {

    List<MonitoringAlertDocument> findAllByOrderByTimestampDesc(Pageable pageable);
}