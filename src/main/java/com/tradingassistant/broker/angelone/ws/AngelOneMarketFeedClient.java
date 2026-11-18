package com.tradingassistant.broker.angelone.ws;

import tools.jackson.databind.ObjectMapper;
import com.tradingassistant.broker.angelone.AngelOneSessionManager;
import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.broker.api.MarketFeedListener;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerWebSocketException;
import com.tradingassistant.broker.model.FeedSubscription;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Creates authenticated Streaming 2.0 connections. Authentication headers are
 * applied only to the WebSocket handshake and are never logged.
 */
public class AngelOneMarketFeedClient {

    private final AngelOneProperties properties;
    private final AngelOneSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final BinaryTickDecoder decoder = new BinaryTickDecoder();

    public AngelOneMarketFeedClient(AngelOneProperties properties,
                                    AngelOneSessionManager sessionManager,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    public MarketFeedConnection open(FeedSubscription subscription, MarketFeedListener listener) {
        return open(subscription, listener, true);
    }

    private MarketFeedConnection open(FeedSubscription subscription,
                                      MarketFeedListener listener,
                                      boolean retryOnUnauthorized) {
        if (subscription == null) {
            throw new IllegalArgumentException("subscription must not be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        String accessToken = sessionManager.accessToken();
        String feedToken = sessionManager.feedToken();
        WebSocket.Builder builder = httpClient.newWebSocketBuilder()
                .connectTimeout(properties.connectTimeout())
                // The official Python SDK sends the raw JWT in this header.
                .header("Authorization", accessToken)
                .header("x-api-key", properties.apiKey())
                .header("x-client-code", properties.clientCode())
                .header("x-feed-token", feedToken);

        AngelOneFeedConnection connection = new AngelOneFeedConnection(
                subscription, listener, objectMapper, decoder);
        CompletableFuture<WebSocket> future = builder.buildAsync(properties.websocketUrl(), connection);
        try {
            WebSocket socket = future.get(properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
            connection.attach(socket);
            connection.start();
            return connection;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new BrokerWebSocketException("The market-feed connection was interrupted.", exception);
        } catch (ExecutionException exception) {
            future.cancel(true);
            if (retryOnUnauthorized && isUnauthorized(exception)) {
                sessionManager.forceRenew();
                return open(subscription, listener, false);
            }
            throw new BrokerWebSocketException("The market-feed connection could not be established.", exception);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new BrokerWebSocketException("The market-feed connection could not be established.", exception);
        }
    }

    private static boolean isUnauthorized(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof WebSocketHandshakeException handshake
                    && handshake.getResponse().statusCode() == 401) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
