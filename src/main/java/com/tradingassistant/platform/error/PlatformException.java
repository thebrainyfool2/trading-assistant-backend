package com.tradingassistant.platform.error;

import com.tradingassistant.common.api.ErrorCode;
import com.tradingassistant.common.api.FieldViolation;

import java.util.List;

/**
 * Application-layer failure that already carries the public error code.
 *
 * <p>Services throw this instead of HTTP exceptions, so the platform layer stays
 * independent of the web layer while the centralized handler still produces the
 * stable error envelope.</p>
 */
public class PlatformException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldViolation> details;

    public PlatformException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public PlatformException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public PlatformException(ErrorCode errorCode, String message,
                             List<FieldViolation> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<FieldViolation> details() {
        return details;
    }

    public static PlatformException of(ErrorCode code, String field, String message) {
        return new PlatformException(code, message,
                List.of(new FieldViolation(field, message)));
    }
}
