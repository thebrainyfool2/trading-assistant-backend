package com.tradingassistant.recommendation.exception;

/**
 * Rejected input, configuration or candidate recommendation.
 */
public class RecommendationValidationException extends RecommendationException {

    public RecommendationValidationException(String message) {
        super(message);
    }

    public RecommendationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
