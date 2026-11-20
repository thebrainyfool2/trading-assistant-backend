package com.tradingassistant.monitoring.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Latest result repository, keyed by manual trade id. */
public interface MonitoringResultRepository
        extends MongoRepository<MonitoringResultDocument, String> {
}