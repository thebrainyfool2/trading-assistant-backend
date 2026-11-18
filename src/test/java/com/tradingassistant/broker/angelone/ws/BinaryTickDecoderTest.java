package com.tradingassistant.broker.angelone.ws;

import com.tradingassistant.broker.exception.BrokerWebSocketException;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedTick;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryTickDecoderTest {

    private final BinaryTickDecoder decoder = new BinaryTickDecoder();

    @Test
    void decodesLtpPacket() {
        byte[] packet = new byte[51];
        ByteBuffer buffer = littleEndian(packet);
        buffer.put(0, (byte) 1);
        buffer.put(1, (byte) 1);
        putToken(buffer, "3045");
        buffer.putLong(27, 77L);
        buffer.putLong(35, 1_700_000_000_000L);
        buffer.putLong(43, 12_345L);

        FeedTick tick = decoder.decode(packet);

        assertThat(tick.mode()).isEqualTo(FeedMode.LTP);
        assertThat(tick.exchange()).isEqualTo(Exchange.NSE);
        assertThat(tick.symbolToken()).isEqualTo("3045");
        assertThat(tick.sequenceNumber()).isEqualTo(77L);
        assertThat(tick.exchangeTimestamp()).isEqualTo(Instant.ofEpochMilli(1_700_000_000_000L));
        assertThat(tick.lastTradedPrice()).isEqualByComparingTo("123.45");
        assertThat(tick.depth()).isNull();
    }

    @Test
    void decodesQuotePacketFields() {
        byte[] packet = new byte[123];
        ByteBuffer buffer = littleEndian(packet);
        buffer.put(0, (byte) 2);
        buffer.put(1, (byte) 1);
        putToken(buffer, "3045");
        buffer.putLong(27, 88L);
        buffer.putLong(35, 1_700_000_000_000L);
        buffer.putLong(43, 80_000L);
        buffer.putLong(51, 10L);
        buffer.putLong(59, 79_900L);
        buffer.putLong(67, 1_000L);
        buffer.putDouble(75, 500.0);
        buffer.putDouble(83, 600.0);
        buffer.putLong(91, 79_500L);
        buffer.putLong(99, 80_500L);
        buffer.putLong(107, 79_000L);
        buffer.putLong(115, 79_800L);

        FeedTick tick = decoder.decode(packet);

        assertThat(tick.mode()).isEqualTo(FeedMode.QUOTE);
        assertThat(tick.lastTradedPrice()).isEqualByComparingTo("800.00");
        assertThat(tick.lastTradedQuantity()).isEqualTo(10L);
        assertThat(tick.averageTradedPrice()).isEqualByComparingTo("799.00");
        assertThat(tick.tradeVolume()).isEqualTo(1_000L);
        assertThat(tick.totalBuyQuantity()).isEqualTo(500L);
        assertThat(tick.totalSellQuantity()).isEqualTo(600L);
        assertThat(tick.open()).isEqualByComparingTo("795.00");
        assertThat(tick.high()).isEqualByComparingTo("805.00");
        assertThat(tick.low()).isEqualByComparingTo("790.00");
        assertThat(tick.close()).isEqualByComparingTo("798.00");
        assertThat(tick.depth()).isNull();
    }

    @Test
    void decodesSnapQuoteDepthAndCircuitLimits() {
        byte[] packet = new byte[379];
        ByteBuffer buffer = littleEndian(packet);
        buffer.put(0, (byte) 3);
        buffer.put(1, (byte) 1);
        putToken(buffer, "3045");
        buffer.putLong(27, 99L);
        buffer.putLong(35, 1_700_000_000_000L);
        buffer.putLong(43, 80_000L);
        buffer.putLong(51, 10L);
        buffer.putLong(59, 79_900L);
        buffer.putLong(67, 1_000L);
        buffer.putDouble(75, 500.0);
        buffer.putDouble(83, 600.0);
        buffer.putLong(91, 79_500L);
        buffer.putLong(99, 80_500L);
        buffer.putLong(107, 79_000L);
        buffer.putLong(115, 79_800L);
        buffer.putLong(123, 1_700_000_000_000L);
        buffer.putLong(131, 42L);
        for (int index = 0; index < 5; index++) {
            int offset = 147 + index * 20;
            buffer.putShort(offset, (short) 0);
            buffer.putLong(offset + 2, 10L + index);
            buffer.putLong(offset + 10, 80_000L + index * 5L);
            buffer.putShort(offset + 18, (short) 2);
        }
        for (int index = 0; index < 5; index++) {
            int offset = 147 + (index + 5) * 20;
            buffer.putShort(offset, (short) 1);
            buffer.putLong(offset + 2, 20L + index);
            buffer.putLong(offset + 10, 80_050L + index * 5L);
            buffer.putShort(offset + 18, (short) 3);
        }
        buffer.putLong(347, 85_000L);
        buffer.putLong(355, 75_000L);
        buffer.putLong(363, 90_000L);
        buffer.putLong(371, 60_000L);

        FeedTick tick = decoder.decode(packet);

        assertThat(tick.mode()).isEqualTo(FeedMode.SNAP_QUOTE);
        assertThat(tick.openInterest()).isEqualTo(42L);
        assertThat(tick.depth().buy()).hasSize(5);
        assertThat(tick.depth().sell()).hasSize(5);
        assertThat(tick.depth().buy().getFirst().price()).isEqualByComparingTo("800.00");
        assertThat(tick.depth().sell().getFirst().price()).isEqualByComparingTo("800.50");
        assertThat(tick.upperCircuit()).isEqualByComparingTo("850.00");
        assertThat(tick.lowerCircuit()).isEqualByComparingTo("750.00");
        assertThat(tick.week52High()).isEqualByComparingTo("900.00");
        assertThat(tick.week52Low()).isEqualByComparingTo("600.00");
    }

    @Test
    void rejectsShortPacketsAndUnknownModes() {
        assertThatThrownBy(() -> decoder.decode(new byte[10]))
                .isInstanceOf(BrokerWebSocketException.class);
        byte[] packet = new byte[51];
        packet[0] = 9;
        assertThatThrownBy(() -> decoder.decode(packet))
                .isInstanceOf(BrokerWebSocketException.class);
    }

    private static ByteBuffer littleEndian(byte[] packet) {
        return ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static void putToken(ByteBuffer buffer, String token) {
        byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < bytes.length; index++) {
            buffer.put(2 + index, bytes[index]);
        }
    }
}
