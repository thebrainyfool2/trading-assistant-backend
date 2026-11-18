package com.tradingassistant.common.api;

import org.slf4j.MDC;

/**
 * Access to request-scoped values without coupling the API layer to a servlet
 * request object. The value is populated by the request correlation filter.
 */
public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String UNKNOWN_REQUEST_ID = "unknown";

    private RequestContext() {
    }

    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        return requestId == null || requestId.isBlank() ? UNKNOWN_REQUEST_ID : requestId;
    }
}
