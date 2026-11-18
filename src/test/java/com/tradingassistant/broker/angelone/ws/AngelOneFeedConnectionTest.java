package com.tradingassistant.broker.angelone.ws;

import com.tradingassistant.broker.api.MarketFeedListener;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedSubscription;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AngelOneFeedConnectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocket webSocket = mock(WebSocket.class);
    private final MarketFeedListener listener = mock(MarketFeedListener.class);
    private final FeedSubscription subscription = new FeedSubscription(
            FeedMode.LTP, Map.of(Exchange.NSE, List.of("3045")), "abcde12345");

    @Test
    void sendsDocumentedSubscriptionAndNotifiesConnected() {
        when(webSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        AngelOneFeedConnection connection = new AngelOneFeedConnection(
                subscription, listener, objectMapper, new BinaryTickDecoder());
        connection.attach(webSocket);

        connection.start();

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(webSocket).sendText(payload.capture(), eq(true));
        JsonNode message = objectMapper.readTree(payload.getValue());
        assertThat(message.path("correlationID").asText()).isEqualTo("abcde12345");
        assertThat(message.path("action").asInt()).isEqualTo(1);
        assertThat(message.path("params").path("mode").asInt()).isEqualTo(1);
        assertThat(message.path("params").path("tokenList").get(0).path("exchangeType").asInt()).isEqualTo(1);
        assertThat(message.path("params").path("tokenList").get(0).path("tokens").get(0).asText()).isEqualTo("3045");
        verify(listener).onConnected();
        assertThat(connection.isOpen()).isTrue();

        connection.close();
    }

    @Test
    void answersPingWithPong() {
        when(webSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        AngelOneFeedConnection connection = new AngelOneFeedConnection(
                subscription, listener, objectMapper, new BinaryTickDecoder());
        connection.attach(webSocket);

        connection.onText(webSocket, "ping", true);

        verify(webSocket).sendText("pong", true);
    }

    @Test
    void decodesBinaryTickAndRequestsMoreData() {
        when(webSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        AngelOneFeedConnection connection = new AngelOneFeedConnection(
                subscription, listener, objectMapper, new BinaryTickDecoder());
        connection.attach(webSocket);

        byte[] packet = new byte[51];
        ByteBuffer buffer = ByteBuffer.wrap(packet).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.put(0, (byte) 1);
        buffer.put(1, (byte) 1);
        byte[] token = "3045".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int index = 0; index < token.length; index++) {
            buffer.put(2 + index, token[index]);
        }
        buffer.putLong(27, 7L);
        buffer.putLong(35, 1_700_000_000_000L);
        buffer.putLong(43, 12_345L);

        connection.onBinary(webSocket, ByteBuffer.wrap(packet), true);

        verify(listener).onTick(org.mockito.ArgumentMatchers.argThat(
                tick -> tick.symbolToken().equals("3045")
                        && tick.lastTradedPrice().compareTo(new java.math.BigDecimal("123.45")) == 0));
        verify(webSocket).request(1);
    }

    @Test
    void closeIsGracefulAndIdempotent() {
        when(webSocket.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(webSocket));
        when(webSocket.sendClose(anyInt(), anyString())).thenReturn(CompletableFuture.completedFuture(webSocket));
        AngelOneFeedConnection connection = new AngelOneFeedConnection(
                subscription, listener, objectMapper, new BinaryTickDecoder());
        connection.attach(webSocket);

        connection.close();
        connection.close();

        verify(webSocket).sendClose(WebSocket.NORMAL_CLOSURE, "client shutdown");
        verify(listener).onDisconnected(1000, "client shutdown");
        assertThat(connection.isOpen()).isFalse();
    }
}
