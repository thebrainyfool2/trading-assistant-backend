package com.tradingassistant.common.error;

import com.tradingassistant.common.api.ApiErrorResponse;
import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.common.api.FieldViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Converts exceptions into the platform's stable error contract. Client errors
 * are logged at debug level; unexpected server errors are logged at error level
 * without exposing implementation details in the response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<FieldViolation> details = exception.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toFieldViolation)
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        log.debug("Request validation failed: {}", details);
        return build(ErrorCode.VALIDATION_FAILED, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        List<FieldViolation> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(),
                        Optional.ofNullable(violation.getMessage()).orElse("Invalid value.")
                ))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        log.debug("Constraint validation failed: {}", details);
        return build(ErrorCode.VALIDATION_FAILED, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        log.debug("Request body could not be read", exception);
        return build(ErrorCode.MALFORMED_REQUEST, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        FieldViolation detail = new FieldViolation(exception.getName(), "Invalid value.");
        log.debug("Request argument type mismatch: {}", detail);
        return build(ErrorCode.VALIDATION_FAILED, List.of(detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception) {
        FieldViolation detail = new FieldViolation(exception.getParameterName(), "Parameter is required.");
        log.debug("Required request parameter is missing: {}", detail);
        return build(ErrorCode.VALIDATION_FAILED, List.of(detail));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException exception) {
        log.debug("No resource found for request", exception);
        return build(ErrorCode.RESOURCE_NOT_FOUND, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.debug("HTTP method not supported: {}", exception.getMethod());
        return build(ErrorCode.METHOD_NOT_ALLOWED, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception) {
        log.debug("Unsupported request media type: {}", exception.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled exception while processing request", exception);
        return build(ErrorCode.INTERNAL_ERROR, List.of());
    }

    private static FieldViolation toFieldViolation(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        String message = Optional.ofNullable(error.getDefaultMessage()).orElse("Invalid value.");
        return new FieldViolation(field, message);
    }

    private static ResponseEntity<ApiErrorResponse> build(ErrorCode errorCode, List<FieldViolation> details) {
        return ResponseEntity
                .status(errorCode.status())
                .body(ApiErrorResponse.of(errorCode, details));
    }
}
