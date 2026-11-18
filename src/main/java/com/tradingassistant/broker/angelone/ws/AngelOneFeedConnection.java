package com.tradingassistant.broker.angelone.ws;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.broker.api.MarketFeedListener;
import com.tradingassistant.broker.exception.BrokerWebSocketException;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.broker.model.FeedTick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One authenticated Streaming 2.0 connection.
 *
 * <p>The implementation sends the documented JSON subscription, sends a text
 * heartbeat every 30 seconds, decodes binary packets and performs a graceful
 * WebSocket close. Reconnection is intentionally left to the application layer
 * in Phase 2.</p>
 */
public final class AngelOneFeedConnection implements WebSocket.Listener, MarketFeedConnection {

    private static final Logger log = LoggerFactory.getLogger(AngelOneFeedConnection.class);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final FeedSubscription subscription;
    private final MarketFeedListener listener;
    private final ObjectMapper objectMapper;
    private final BinaryTickDecoder decoder;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean closeNotified = new AtomicBoolean(false);
    private final ByteArrayOutputStream partialPacket = new ByteArrayOutputStream();

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> heartbeat;

    AngelOneFeedConnection(FeedSubscription subscription,
                           MarketFeedListener listener,
                           ObjectMapper objectMapper,
                           BinaryTickDecoder decoder) {
        this.subscription = subscription;
        this.listener = listener;
        this.objectMapper = objectMapper;
        this.decoder = decoder;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "angel-one-market-feed");
            thread.setDaemon(true);
            return thread;
        });
    }

    void attach(WebSocket webSocket) {
        this.webSocket = webSocket;
    }

    void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (webSocket == null || closed.get()) {
            close();
            throw new BrokerWebSocketException("The market-feed connection was not open.");
        }
        try {
            sendSubscription();
            heartbeat = scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    HEARTBEAT_INTERVAL.toMillis(),
                    HEARTBEAT_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS);
            notifyConnected();
        } catch (RuntimeException exception) {
            close();
            throw new BrokerWebSocketException("The market-feed subscription could not be sent.", exception);
        }
    }

    @Override
    public boolean isOpen() {
        WebSocket socket = webSocket;
        return !closed.get() && socket != null && !socket.isInputClosed() && !socket.isOutputClosed();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopHeartbeat();
        scheduler.shutdownNow();
        WebSocket socket = webSocket;
        if (socket == null) {
            notifyDisconnected(1000, "client shutdown");
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown")
                    .orTimeout(CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(exception -> {
                        socket.abort();
                        return null;
                    })
                    .join();
        } catch (RuntimeException exception) {
            socket.abort();
        } finally {
            notifyDisconnected(1000, "client shutdown");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        attach(webSocket);
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String text = data.toString();
        if ("ping".equalsIgnoreCase(text)) {
            webSocket.sendText("pong", true);
        } else if (!"pong".equalsIgnoreCase(text) && !text.isBlank()) {
            notifyBrokerError(text);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        byte[] chunk = new byte[data.remaining()];
        data.get(chunk);
        byte[] packet;
        synchronized (partialPacket) {
            partialPacket.writeBytes(chunk);
            if (!last) {
                webSocket.request(1);
                return null;
            }
            packet = partialPacket.toByteArray();
            partialPacket.reset();
        }
        try {
            FeedTick tick = decoder.decode(packet);
            notifyTick(tick);
        } catch (BrokerWebSocketException exception) {
            notifyError(exception);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closed.set(true);
        stopHeartbeat();
        scheduler.shutdownNow();
        notifyDisconnected(statusCode, reason);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        closed.set(true);
        stopHeartbeat();
        scheduler.shutdownNow();
        notifyError(new BrokerWebSocketException("The market-feed connection failed.", error));
        notifyDisconnected(1006, "connection error");
    }

    private void sendSubscription() {
        List<Map<String, Object>> tokenList = new ArrayList<>();
        for (Map.Entry<Exchange, List<String>> entry : subscription.tokens().entrySet()) {
            Map<String, Object> exchangeTokens = new LinkedHashMap<>();
            exchangeTokens.put("exchangeType", entry.getKey().feedType());
            exchangeTokens.put("tokens", entry.getValue());
            tokenList.add(exchangeTokens);
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("correlationID", subscription.correlationId());
        message.put("action", 1);
        message.put("params", Map.of(
                "mode", subscription.mode().protocolValue(),
                "tokenList", tokenList));
        try {
            String payload = objectMapper.writeValueAsString(message);
            webSocket.sendText(payload, true).join();
        } catch (RuntimeException exception) {
            throw new BrokerWebSocketException("The market-feed subscription was rejected.", exception);
        }
    }

    private void sendHeartbeat() {
        WebSocket socket = webSocket;
        if (closed.get() || socket == null || socket.isOutputClosed()) {
            return;
        }
        try {
            socket.sendText("ping", true).exceptionally(exception -> {
                notifyError(new BrokerWebSocketException("The market-feed heartbeat failed.", exception));
                return null;
            });
        } catch (RuntimeException exception) {
            notifyError(new BrokerWebSocketException("The market-feed heartbeat failed.", exception));
        }
    }

    private void notifyBrokerError(String text) {
        try {
            JsonNode root = objectMapper.readTree(text);
            String code = root.path("errorCode").asText("");
            if (!code.isBlank()) {
                notifyError(new BrokerWebSocketException("The broker rejected the market-feed subscription (" + code + ")."));
            }
        } catch (Exception ignored) {
            // A non-control text message without a documented error shape is ignored.
        }
    }

    private void notifyConnected() {
        try {
            listener.onConnected();
        } catch (RuntimeException exception) {
            log.warn("broker_feed_listener_connected_failed errorType={}", exception.getClass().getSimpleName());
        }
    }

    private void notifyTick(FeedTick tick) {
        try {
            listener.onTick(tick);
        } catch (RuntimeException exception) {
            log.warn("broker_feed_listener_tick_failed errorType={}", exception.getClass().getSimpleName());
        }
    }

    private void notifyDisconnected(int statusCode, String reason) {
        if (!closeNotified.compareAndSet(false, true)) {
            return;
        }
        try {
            listener.onDisconnected(statusCode, reason == null ? "" : reason);
        } catch (RuntimeException exception) {
            log.warn("broker_feed_listener_disconnected_failed errorType={}", exception.getClass().getSimpleName());
        }
        log.info("broker_feed_disconnected statusCode={}", statusCode);
    }

    private void notifyError(Throwable error) {
        try {
            listener.onError(error);
        } catch (RuntimeException listenerException) {
            log.warn("broker_feed_listener_error_failed errorType={}", listenerException.getClass().getSimpleName());
        }
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> current = heartbeat;
        if (current != null) {
            current.cancel(false);
        }
    }
}
