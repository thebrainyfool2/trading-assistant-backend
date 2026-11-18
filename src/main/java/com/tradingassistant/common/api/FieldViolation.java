package com.tradingassistant.common.api;

/**
 * A single validation or request-binding failure. Keeping this separate from the
 * human-readable error message allows clients to highlight individual fields.
 */
public record FieldViolation(String field, String message) {
}
