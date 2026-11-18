package com.tradingassistant.common.api;

import java.util.List;

/**
 * Machine-readable error details returned by the centralized exception handler.
 */
public record ApiError(String code, String message, List<FieldViolation> details) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}
