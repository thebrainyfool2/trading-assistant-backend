package com.tradingassistant.recommendation.exception;

/**
 * Base exception for deterministic recommendation failures.
 */
public class RecommendationException extends RuntimeException {

    public RecommendationException(String message) {
        super(message);
    }

    public RecommendationException(String message, Throwable cause) {
        super(message, cause);
    }
}
