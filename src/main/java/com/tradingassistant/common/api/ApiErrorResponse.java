package com.tradingassistant.common.api;

import java.util.List;

/**
 * Standard error envelope returned by the centralized exception handler.
 */
public record ApiErrorResponse(boolean success, ApiError error, ApiMeta meta) {

    public static ApiErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.defaultMessage(), List.of());
    }

    public static ApiErrorResponse of(ErrorCode errorCode, List<FieldViolation> details) {
        return of(errorCode, errorCode.defaultMessage(), details);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String message, List<FieldViolation> details) {
        return new ApiErrorResponse(
                false,
                new ApiError(errorCode.name(), message, details),
                ApiMeta.current()
        );
    }
}
