package com.tradingassistant.broker.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A validated WebSocket subscription. The broker counts each token-mode pair
 * against a 1,000-subscription limit; duplicate tokens in one exchange are
 * removed before sending.
 */
public record FeedSubscription(FeedMode mode, Map<Exchange, List<String>> tokens, String correlationId) {

    public static final int MAX_SUBSCRIPTIONS = 1000;

    public FeedSubscription {
        Objects.requireNonNull(mode, "mode must not be null");
        Objects.requireNonNull(tokens, "tokens must not be null");
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("At least one exchange token is required");
        }

        Map<Exchange, List<String>> normalized = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<Exchange, List<String>> entry : tokens.entrySet()) {
            Exchange exchange = Objects.requireNonNull(entry.getKey(), "exchange must not be null");
            if (!exchange.supportsMarketFeed()) {
                throw new IllegalArgumentException("Exchange " + exchange.code() + " is not supported by the market feed");
            }
            Set<String> uniqueTokens = new LinkedHashSet<>();
            for (String token : entry.getValue() == null ? List.<String>of() : entry.getValue()) {
                if (token != null && !token.isBlank()) {
                    uniqueTokens.add(token.trim());
                }
            }
            if (uniqueTokens.isEmpty()) {
                continue;
            }
            count += uniqueTokens.size();
            normalized.put(exchange, List.copyOf(uniqueTokens));
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one non-blank token is required");
        }
        if (count > MAX_SUBSCRIPTIONS) {
            throw new IllegalArgumentException("Subscription exceeds the documented limit of "
                    + MAX_SUBSCRIPTIONS + " token-mode entries");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }
        if (!correlationId.matches("^[A-Za-z0-9]{10}$")) {
            throw new IllegalArgumentException("correlationId must be 10 alphanumeric characters");
        }
        tokens = Collections.unmodifiableMap(normalized);
    }

    public FeedSubscription(FeedMode mode, Map<Exchange, List<String>> tokens) {
        this(mode, tokens, null);
    }

    public int subscriptionCount() {
        return tokens.values().stream().mapToInt(List::size).sum();
    }
}
