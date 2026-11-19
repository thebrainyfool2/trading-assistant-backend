package com.tradingassistant.common.api;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes exposed by the API. Messages are safe for
 * clients and never contain internal exception details.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this resource."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The request media type is not supported."),
    BROKER_AUTHENTICATION_FAILED(HttpStatus.BAD_GATEWAY, "The broker could not authenticate the configured account."),
    BROKER_SESSION_EXPIRED(HttpStatus.SERVICE_UNAVAILABLE, "The broker session is not available."),
    BROKER_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "The broker rate limit was exceeded."),
    BROKER_REQUEST_REJECTED(HttpStatus.BAD_GATEWAY, "The broker rejected the request."),
    BROKER_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "The broker returned an invalid response."),
    BROKER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The broker is temporarily unavailable."),
    BROKER_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "The broker configuration is invalid."),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required or the credentials are invalid."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "The authenticated account may not perform this action."),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with existing state."),
    EXECUTION_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY, "The order could not be executed."),
    ENGINE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The trading engine is not available in this environment."),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The database is temporarily unavailable."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
