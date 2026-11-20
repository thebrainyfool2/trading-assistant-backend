package com.tradingassistant.monitoring.persistence;

import com.tradingassistant.monitoring.domain.ManualTradeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/** Spring Data repository for manually recorded trades. */
public interface ManualTradeRepository extends MongoRepository<ManualTradeDocument, String> {

    List<ManualTradeDocument> findByStatusOrderByEntryTimestampAsc(ManualTradeStatus status);
}