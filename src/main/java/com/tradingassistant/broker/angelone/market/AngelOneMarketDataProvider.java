package com.tradingassistant.broker.angelone.market;

import com.tradingassistant.broker.angelone.AngelOneSessionManager;
import com.tradingassistant.broker.angelone.dto.AngelOneEnvelope;
import com.tradingassistant.broker.angelone.dto.CandleRequestDto;
import com.tradingassistant.broker.angelone.dto.DepthData;
import com.tradingassistant.broker.angelone.dto.QuoteData;
import com.tradingassistant.broker.angelone.dto.QuoteRequest;
import com.tradingassistant.broker.angelone.dto.QuoteResponseData;
import com.tradingassistant.broker.angelone.http.AngelOneHttpClient;
import com.tradingassistant.broker.angelone.http.RetryPolicy;
import com.tradingassistant.broker.angelone.ws.AngelOneMarketFeedClient;
import com.tradingassistant.broker.api.MarketDataProvider;
import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.broker.api.MarketFeedListener;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.exception.BrokerInvalidResponseException;
import com.tradingassistant.broker.exception.BrokerSessionExpiredException;
import com.tradingassistant.broker.model.Candle;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.broker.model.DepthLevel;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.broker.model.InstrumentMaster;
import com.tradingassistant.broker.model.MarketDepth;
import com.tradingassistant.broker.model.Quote;
import com.tradingassistant.broker.model.QuoteMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Read-only SmartAPI market-data implementation. The quote and candle requests
 * are POSTs because of the broker contract, but they are safe to repeat and use
 * the bounded read-only retry policy.
 */
public class AngelOneMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AngelOneMarketDataProvider.class);

    private static final String QUOTE_PATH = "/rest/secure/angelbroking/market/v1/quote/";
    private static final String CANDLE_PATH = "/rest/secure/angelbroking/historical/v1/getCandleData";
    private static final int MAX_QUOTE_TOKENS = 50;
    private static final DateTimeFormatter CANDLE_REQUEST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final ParameterizedTypeReference<AngelOneEnvelope<QuoteResponseData>> QUOTE_ENVELOPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<AngelOneEnvelope<List<List<Object>>>> CANDLE_ENVELOPE =
            new ParameterizedTypeReference<>() {
            };

    private final AngelOneHttpClient httpClient;
    private final AngelOneSessionManager sessionManager;
    private final AngelOneProperties properties;
    private final AngelOneInstrumentMasterClient instrumentMasterClient;
    private final AngelOneMarketFeedClient feedClient;

    public AngelOneMarketDataProvider(AngelOneHttpClient httpClient,
                                      AngelOneSessionManager sessionManager,
                                      AngelOneProperties properties,
                                      Clock clock,
                                      AngelOneMarketFeedClient feedClient) {
        this.httpClient = httpClient;
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.instrumentMasterClient = new AngelOneInstrumentMasterClient(httpClient, properties, clock);
        this.feedClient = feedClient;
    }

    @Override
    public List<Quote> quotes(QuoteMode mode, Map<Exchange, List<String>> exchangeTokens) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (exchangeTokens == null || exchangeTokens.isEmpty()) {
            throw new IllegalArgumentException("At least one exchange token is required");
        }

        Map<String, List<String>> requestTokens = new LinkedHashMap<>();
        int tokenCount = 0;
        for (Map.Entry<Exchange, List<String>> entry : exchangeTokens.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            List<String> tokens = entry.getValue().stream()
                    .filter(token -> token != null && !token.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            if (!tokens.isEmpty()) {
                requestTokens.put(entry.getKey().code(), tokens);
                tokenCount += tokens.size();
            }
        }
        if (requestTokens.isEmpty()) {
            throw new IllegalArgumentException("At least one non-blank token is required");
        }
        if (tokenCount > MAX_QUOTE_TOKENS) {
            throw new IllegalArgumentException("Quote request exceeds the documented limit of "
                    + MAX_QUOTE_TOKENS + " symbols");
        }

        AngelOneEnvelope<QuoteResponseData> response = withSessionRetry(() -> httpClient.post(
                QUOTE_PATH,
                new QuoteRequest(mode.name(), requestTokens),
                QUOTE_ENVELOPE,
                sessionManager.accessToken(),
                RetryPolicy.safe(properties)));
        QuoteResponseData data = response.data();
        if (data == null || data.fetched() == null) {
            throw new BrokerInvalidResponseException("The quote response contained no data.");
        }
        if (data.unfetched() != null && !data.unfetched().isEmpty()) {
            log.warn("broker_quote_partial_failure unfetched={}", data.unfetched().size());
        }

        List<Quote> quotes = new ArrayList<>(data.fetched().size());
        for (QuoteData row : data.fetched()) {
            quotes.add(toQuote(row));
        }
        return List.copyOf(quotes);
    }

    @Override
    public List<Candle> candles(CandleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        CandleRequestDto dto = new CandleRequestDto(
                request.exchange().code(),
                request.symbolToken(),
                request.interval().code(),
                CANDLE_REQUEST_TIME.format(request.from()),
                CANDLE_REQUEST_TIME.format(request.to()));
        AngelOneEnvelope<List<List<Object>>> response = withSessionRetry(() -> httpClient.post(
                CANDLE_PATH,
                dto,
                CANDLE_ENVELOPE,
                sessionManager.accessToken(),
                RetryPolicy.safe(properties)));
        if (response.data() == null) {
            throw new BrokerInvalidResponseException("The candle response contained no data.");
        }
        List<Candle> candles = new ArrayList<>(response.data().size());
        for (List<Object> row : response.data()) {
            candles.add(toCandle(row));
        }
        return List.copyOf(candles);
    }

    @Override
    public InstrumentMaster instruments() {
        return instrumentMasterClient.load();
    }

    @Override
    public MarketFeedConnection openMarketFeed(FeedSubscription subscription, MarketFeedListener listener) {
        return feedClient.open(subscription, listener);
    }

    private <T> T withSessionRetry(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BrokerSessionExpiredException exception) {
            sessionManager.forceRenew();
            return operation.get();
        }
    }

    private static Quote toQuote(QuoteData row) {
        if (row == null || row.exchange() == null || row.symbolToken() == null) {
            throw new BrokerInvalidResponseException("A quote row was missing its exchange or token.");
        }
        Exchange exchange;
        try {
            exchange = Exchange.valueOf(row.exchange().trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BrokerInvalidResponseException("A quote row used an unknown exchange.", exception);
        }
        return new Quote(
                exchange,
                row.symbolToken(),
                row.tradingSymbol(),
                row.ltp(),
                row.open(),
                row.high(),
                row.low(),
                row.close(),
                row.lastTradeQty(),
                row.netChange(),
                row.percentChange(),
                row.avgPrice(),
                row.tradeVolume(),
                row.openInterest(),
                row.upperCircuit(),
                row.lowerCircuit(),
                row.week52High(),
                row.week52Low(),
                row.totalBuyQuantity(),
                row.totalSellQuantity(),
                toDepth(row.depth()));
    }

    private static MarketDepth toDepth(DepthData depth) {
        if (depth == null) {
            return new MarketDepth(List.of(), List.of());
        }
        return new MarketDepth(toLevels(depth.buy()), toLevels(depth.sell()));
    }

    private static List<DepthLevel> toLevels(List<DepthData.Level> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .map(level -> new DepthLevel(level.price(), level.quantity(), level.orders()))
                .toList();
    }

    private static Candle toCandle(List<Object> row) {
        if (row == null || row.size() < 6) {
            throw new BrokerInvalidResponseException("A candle row did not contain six fields.");
        }
        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(String.valueOf(row.get(0)));
            return new Candle(
                    timestamp,
                    decimal(row.get(1)),
                    decimal(row.get(2)),
                    decimal(row.get(3)),
                    decimal(row.get(4)),
                    decimal(row.get(5)).longValue());
        } catch (RuntimeException exception) {
            throw new BrokerInvalidResponseException("A candle row could not be parsed.", exception);
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Numeric value was null");
        }
        return new BigDecimal(String.valueOf(value));
    }
}
