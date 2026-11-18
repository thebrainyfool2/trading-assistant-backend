package com.tradingassistant.common.api;

/**
 * Standard success envelope returned by application REST endpoints.
 *
 * @param success always true for a successful response
 * @param data    endpoint-specific response payload
 * @param meta    response metadata, including request correlation information
 * @param <T>     response payload type
 */
public record ApiResponse<T>(boolean success, T data, ApiMeta meta) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, ApiMeta.current());
    }
}
