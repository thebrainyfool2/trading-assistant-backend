package com.tradingassistant.broker.angelone.ws;

import com.tradingassistant.broker.exception.BrokerWebSocketException;
import com.tradingassistant.broker.model.DepthLevel;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedTick;
import com.tradingassistant.broker.model.MarketDepth;

import java.math.BigDecimal;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes SmartAPI Streaming 2.0 binary packets. The protocol is little-endian
 * and prices are expressed in paise. Packet field offsets follow the official
 * protocol table; the documented 5-level depth block is followed by upper/lower
 * circuit and 52-week values for snap-quote packets.
 */
public final class BinaryTickDecoder {

    private static final int LTP_PACKET_SIZE = 51;
    private static final int QUOTE_PACKET_SIZE = 123;
    private static final int SNAP_QUOTE_PACKET_SIZE = 379;
    private static final int TOKEN_START = 2;
    private static final int TOKEN_LENGTH = 25;

    public FeedTick decode(byte[] packet) {
        if (packet == null || packet.length < LTP_PACKET_SIZE) {
            throw new BrokerWebSocketException("The market-feed packet was too short.");
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
            int modeValue = Byte.toUnsignedInt(buffer.get(0));
            FeedMode mode = FeedMode.fromProtocolValue(modeValue);
            Exchange exchange = Exchange.fromFeedType(Byte.toUnsignedInt(buffer.get(1)));
            String token = readToken(buffer);

            if (mode == FeedMode.LTP && packet.length < LTP_PACKET_SIZE) {
                throw new BrokerWebSocketException("The LTP packet was incomplete.");
            }
            if (mode == FeedMode.QUOTE && packet.length < QUOTE_PACKET_SIZE) {
                throw new BrokerWebSocketException("The quote packet was incomplete.");
            }
            if (mode == FeedMode.SNAP_QUOTE && packet.length < SNAP_QUOTE_PACKET_SIZE) {
                throw new BrokerWebSocketException("The snap-quote packet was incomplete.");
            }

            long sequence = buffer.getLong(27);
            Instant exchangeTimestamp = Instant.ofEpochMilli(buffer.getLong(35));
            BigDecimal lastTradedPrice = paise(buffer.getLong(43));

            Long lastTradedQuantity = null;
            BigDecimal averageTradedPrice = null;
            Long tradeVolume = null;
            Long totalBuyQuantity = null;
            Long totalSellQuantity = null;
            BigDecimal open = null;
            BigDecimal high = null;
            BigDecimal low = null;
            BigDecimal close = null;
            Instant lastTradedTimestamp = null;
            Long openInterest = null;
            MarketDepth depth = null;
            BigDecimal upperCircuit = null;
            BigDecimal lowerCircuit = null;
            BigDecimal week52High = null;
            BigDecimal week52Low = null;

            if (mode == FeedMode.QUOTE || mode == FeedMode.SNAP_QUOTE) {
                lastTradedQuantity = buffer.getLong(51);
                averageTradedPrice = paise(buffer.getLong(59));
                tradeVolume = buffer.getLong(67);
                totalBuyQuantity = Math.round(buffer.getDouble(75));
                totalSellQuantity = Math.round(buffer.getDouble(83));
                open = paise(buffer.getLong(91));
                high = paise(buffer.getLong(99));
                low = paise(buffer.getLong(107));
                close = paise(buffer.getLong(115));
            }

            if (mode == FeedMode.SNAP_QUOTE) {
                lastTradedTimestamp = Instant.ofEpochMilli(buffer.getLong(123));
                openInterest = buffer.getLong(131);
                depth = readDepth(buffer, 147);
                upperCircuit = paise(buffer.getLong(347));
                lowerCircuit = paise(buffer.getLong(355));
                week52High = paise(buffer.getLong(363));
                week52Low = paise(buffer.getLong(371));
            }

            return new FeedTick(
                    mode,
                    exchange,
                    token,
                    sequence,
                    exchangeTimestamp,
                    lastTradedPrice,
                    lastTradedQuantity,
                    averageTradedPrice,
                    tradeVolume,
                    totalBuyQuantity,
                    totalSellQuantity,
                    open,
                    high,
                    low,
                    close,
                    lastTradedTimestamp,
                    openInterest,
                    depth,
                    upperCircuit,
                    lowerCircuit,
                    week52High,
                    week52Low);
        } catch (BrokerWebSocketException exception) {
            throw exception;
        } catch (BufferUnderflowException | IllegalArgumentException exception) {
            throw new BrokerWebSocketException("The market-feed packet could not be decoded.", exception);
        }
    }

    private static String readToken(ByteBuffer buffer) {
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        for (int index = 0; index < TOKEN_LENGTH; index++) {
            byte value = buffer.get(TOKEN_START + index);
            if (value == 0) {
                return new String(tokenBytes, 0, index, StandardCharsets.UTF_8);
            }
            tokenBytes[index] = value;
        }
        return new String(tokenBytes, StandardCharsets.UTF_8);
    }

    private static MarketDepth readDepth(ByteBuffer buffer, int start) {
        List<DepthLevel> buy = new ArrayList<>(5);
        List<DepthLevel> sell = new ArrayList<>(5);
        for (int index = 0; index < 10; index++) {
            int offset = start + index * 20;
            int flag = Short.toUnsignedInt(buffer.getShort(offset));
            long quantity = buffer.getLong(offset + 2);
            BigDecimal price = paise(buffer.getLong(offset + 10));
            long orders = Short.toUnsignedLong(buffer.getShort(offset + 18));
            DepthLevel level = new DepthLevel(price, quantity, orders);
            if (flag == 0) {
                buy.add(level);
            } else {
                sell.add(level);
            }
        }
        return new MarketDepth(buy, sell);
    }

    private static BigDecimal paise(long value) {
        return BigDecimal.valueOf(value, 2);
    }
}
