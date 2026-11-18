package com.tradingassistant.broker.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedSubscriptionTest {

    @Test
    void normalizesAndDeduplicatesTokens() {
        FeedSubscription subscription = new FeedSubscription(
                FeedMode.LTP,
                Map.of(Exchange.NSE, List.of("3045", " 3045 ", "1594")));

        assertThat(subscription.tokens().get(Exchange.NSE)).containsExactly("3045", "1594");
        assertThat(subscription.subscriptionCount()).isEqualTo(2);
        assertThat(subscription.correlationId()).matches("^[A-Za-z0-9]{10}$");
    }

    @Test
    void rejectsMoreThanOneThousandSubscriptions() {
        List<String> tokens = IntStream.rangeClosed(1, 1001).mapToObj(String::valueOf).toList();

        assertThatThrownBy(() -> new FeedSubscription(FeedMode.LTP, Map.of(Exchange.NSE, tokens)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1000");
    }

    @Test
    void rejectsExchangesWithoutDocumentedFeedSupport() {
        assertThatThrownBy(() -> new FeedSubscription(FeedMode.LTP, Map.of(Exchange.CDS, List.of("1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CDS");
    }

    @Test
    void rejectsInvalidCorrelationIds() {
        assertThatThrownBy(() -> new FeedSubscription(
                FeedMode.LTP, Map.of(Exchange.NSE, List.of("3045")), "not-valid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }
}
