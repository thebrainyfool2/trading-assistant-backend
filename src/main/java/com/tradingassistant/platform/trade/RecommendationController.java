package com.tradingassistant.platform.trade;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.platform.error.PlatformException;
import com.tradingassistant.platform.instruments.InstrumentReferences;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only recommendations. A recommendation is decision support, not an
 * order; the owner executes manually.
 */
@RestController
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/recommendations",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class RecommendationController {

    private final RecommendationQueryService query;

    public RecommendationController(RecommendationQueryService query) {
        this.query = query;
    }

    @GetMapping
    public ApiResponse<List<RecommendationDtos.RecommendationView>> latest(
            @RequestParam(required = false) String instrument,
            @RequestParam(required = false) String interval,
            @RequestParam(defaultValue = "25") int limit) {
        if (instrument == null || instrument.isBlank()) {
            return ApiResponse.success(query.latest(limit));
        }
        InstrumentKey key = InstrumentReferences.parse(instrument);
        Interval parsed = parseInterval(interval);
        return ApiResponse.success(List.of(query.require(key, parsed)));
    }

    private static Interval parseInterval(String value) {
        if (value == null || value.isBlank()) {
            return Interval.FIVE_MINUTE;
        }
        try {
            return Interval.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Unknown interval: " + value);
        }
    }
}
