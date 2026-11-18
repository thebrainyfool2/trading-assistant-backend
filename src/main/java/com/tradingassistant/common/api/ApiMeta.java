package com.tradingassistant.common.api;

import java.time.Instant;

/**
 * Metadata attached to every API response. The request identifier is taken from
 * the current request's MDC entry so it can be correlated with server logs.
 */
public record ApiMeta(Instant timestamp, String requestId) {

    public static ApiMeta current() {
        return new ApiMeta(Instant.now(), RequestContext.currentRequestId());
    }
}
