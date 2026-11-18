package com.tradingassistant.broker.error;

import com.tradingassistant.broker.exception.BrokerError;
import com.tradingassistant.broker.exception.BrokerException;
import com.tradingassistant.common.api.ApiErrorResponse;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.common.api.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates broker failures into the platform error contract. This advice lives
 * inside the broker module so the common application layer does not depend on
 * broker-specific types.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BrokerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BrokerExceptionHandler.class);

    @ExceptionHandler(BrokerException.class)
    public ResponseEntity<ApiErrorResponse> handleBrokerException(BrokerException exception) {
        log.warn("Broker operation failed: error={} brokerCode={} requestId={}",
                exception.error(), exception.brokerCode(), RequestContext.currentRequestId());
        ErrorCode errorCode = map(exception.error());
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiErrorResponse.of(errorCode, List.of()));
    }

    private static ErrorCode map(BrokerError error) {
        return switch (error) {
            case AUTHENTICATION_FAILED, INVALID_TOTP -> ErrorCode.BROKER_AUTHENTICATION_FAILED;
            case SESSION_EXPIRED -> ErrorCode.BROKER_SESSION_EXPIRED;
            case RATE_LIMITED -> ErrorCode.BROKER_RATE_LIMITED;
            case REQUEST_REJECTED -> ErrorCode.BROKER_REQUEST_REJECTED;
            case INVALID_RESPONSE, WEBSOCKET -> ErrorCode.BROKER_INVALID_RESPONSE;
            case CONFIGURATION -> ErrorCode.BROKER_CONFIGURATION_ERROR;
            case NETWORK, UNAVAILABLE -> ErrorCode.BROKER_UNAVAILABLE;
        };
    }
}
