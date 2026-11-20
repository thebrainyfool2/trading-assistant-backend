package com.tradingassistant.platform.instruments;

import com.tradingassistant.common.api.ApiResponse;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.market.domain.InstrumentType;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.platform.error.PlatformException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Search the broker instrument master by company name or symbol.
 */
@RestController
@ConditionalOnProperty(prefix = "app.broker.angel-one", name = "enabled",
        havingValue = "true")
@RequestMapping(path = "/api/v1/instruments",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class InstrumentController {

    private final InstrumentSearchService search;

    public InstrumentController(InstrumentSearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    public ApiResponse<List<InstrumentDtos.InstrumentView>> search(
            @RequestParam String q,
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "20") int limit) {
        Segment parsedSegment = segment == null || segment.isBlank()
                ? null : parseSegment(segment);
        InstrumentType parsedType = type == null || type.isBlank()
                ? null : parseType(type);
        return ApiResponse.success(search.search(q, parsedSegment, parsedType, limit)
                .stream().map(InstrumentDtos.InstrumentView::from).toList());
    }

    private static Segment parseSegment(String value) {
        try {
            return Segment.fromCode(value);
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED, failure.getMessage());
        }
    }

    private static InstrumentType parseType(String value) {
        try {
            return InstrumentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException failure) {
            throw new PlatformException(ErrorCode.VALIDATION_FAILED,
                    "Unknown instrument type: " + value);
        }
    }
}
