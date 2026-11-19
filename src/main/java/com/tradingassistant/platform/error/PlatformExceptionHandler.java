package com.tradingassistant.platform.error;

import com.tradingassistant.common.api.ApiErrorResponse;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.common.api.FieldViolation;
import com.tradingassistant.execution.exception.ExecutionBrokerException;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Optional;

/**
 * Maps platform and infrastructure failures onto the stable error contract.
 *
 * <p>It runs before the generic advice so security and engine exceptions are not
 * flattened into a generic server error. Database failures are reported without
 * connection strings or driver detail.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class PlatformExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PlatformExceptionHandler.class);

    @ExceptionHandler(PlatformException.class)
    public ResponseEntity<ApiErrorResponse> handlePlatform(PlatformException exception) {
        ErrorCode code = exception.errorCode();
        List<FieldViolation> details = exception.details().isEmpty()
                ? List.of() : exception.details();
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, safeMessage(exception, code), details));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        log.debug("Access denied: {}", exception.getMessage());
        return build(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception) {
        log.debug("Authentication failed: {}", exception.getMessage());
        return build(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(ExecutionValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleExecutionValidation(
            ExecutionValidationException exception) {
        log.debug("Execution validation failed: {}", exception.getMessage());
        return ResponseEntity.status(ErrorCode.EXECUTION_REJECTED.status())
                .body(ApiErrorResponse.of(ErrorCode.EXECUTION_REJECTED,
                        safeMessage(exception, ErrorCode.EXECUTION_REJECTED), List.of()));
    }

    @ExceptionHandler(ExecutionBrokerException.class)
    public ResponseEntity<ApiErrorResponse> handleExecutionBroker(
            ExecutionBrokerException exception) {
        log.warn("Execution broker failure: {}", exception.getMessage());
        return build(ErrorCode.BROKER_UNAVAILABLE);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateKey(DuplicateKeyException exception) {
        log.debug("Duplicate key rejected");
        return build(ErrorCode.CONFLICT);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class,
            UncategorizedMongoDbException.class})
    public ResponseEntity<ApiErrorResponse> handleDatabase(Exception exception) {
        log.error("Database operation failed: {}", exception.getMessage());
        return build(ErrorCode.DATABASE_UNAVAILABLE);
    }

    private static ResponseEntity<ApiErrorResponse> build(ErrorCode code) {
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, code.defaultMessage(), List.of()));
    }

    private static String safeMessage(RuntimeException exception, ErrorCode code) {
        return Optional.ofNullable(exception.getMessage())
                .map(String::trim)
                .filter(message -> !message.isEmpty())
                .orElse(code.defaultMessage());
    }
}
