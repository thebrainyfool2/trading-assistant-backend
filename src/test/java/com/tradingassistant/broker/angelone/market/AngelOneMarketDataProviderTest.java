package com.tradingassistant.broker.angelone.market;

import com.tradingassistant.broker.BrokerTestFixtures;
import com.tradingassistant.broker.angelone.AngelOneSessionManager;
import com.tradingassistant.broker.angelone.http.AngelOneHttpClient;
import com.tradingassistant.broker.angelone.ws.AngelOneMarketFeedClient;
import com.tradingassistant.broker.config.AngelOneProperties;
import com.tradingassistant.broker.model.Candle;
import com.tradingassistant.broker.model.CandleInterval;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.Instrument;
import com.tradingassistant.broker.model.InstrumentMaster;
import com.tradingassistant.broker.model.Quote;
import com.tradingassistant.broker.model.QuoteMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AngelOneMarketDataProviderTest {

    private static final String BASE = "https://apiconnect.angelone.in";
    private static final String QUOTE_URL = BASE + "/rest/secure/angelbroking/market/v1/quote/";
    private static final String CANDLE_URL = BASE + "/rest/secure/angelbroking/historical/v1/getCandleData";
    private static final String INSTRUMENT_URL =
            "https://margincalculator.angelone.in/OpenAPI_File/files/OpenAPIScripMaster.json";

    private final AngelOneProperties properties = BrokerTestFixtures.enabledProperties();
    private final AngelOneSessionManager sessionManager = mock(AngelOneSessionManager.class);
    private final AngelOneMarketFeedClient feedClient = mock(AngelOneMarketFeedClient.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneOffset.UTC);
    private MockRestServiceServer server;
    private AngelOneMarketDataProvider provider;

    @BeforeEach
    void setUp() {
        when(sessionManager.accessToken()).thenReturn("test-jwt");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        AngelOneHttpClient httpClient = new AngelOneHttpClient(properties, builder.build(), new ObjectMapper());
        provider = new AngelOneMarketDataProvider(httpClient, sessionManager, properties, clock, feedClient);
    }

    @Test
    void mapsFullQuoteResponseIncludingDepthAnd52WeekValues() {
        server.expect(requestTo(QUOTE_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": true,
                          "message": "SUCCESS",
                          "errorcode": "",
                          "data": {
                            "fetched": [{
                              "exchange": "NSE",
                              "tradingSymbol": "SBIN-EQ",
                              "symbolToken": "3045",
                              "ltp": 800.25,
                              "open": 795.00,
                              "high": 805.00,
                              "low": 790.00,
                              "close": 799.00,
                              "lastTradeQty": 10,
                              "netChange": 1.25,
                              "percentChange": 0.16,
                              "avgPrice": 801.00,
                              "tradeVolume": 123456,
                              "opnInterest": 0,
                              "upperCircuit": 850.00,
                              "lowerCircuit": 750.00,
                              "52WeekHigh": 900.00,
                              "52WeekLow": 600.00,
                              "totBuyQuan": 1000,
                              "totSellQuan": 1200,
                              "depth": {
                                "buy": [{"price": 800.20, "quantity": 5, "orders": 2}],
                                "sell": [{"price": 800.30, "quantity": 7, "orders": 3}]
                              }
                            }],
                            "unfetched": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<Quote> quotes = provider.quotes(QuoteMode.FULL, Map.of(Exchange.NSE, List.of("3045")));

        assertThat(quotes).hasSize(1);
        Quote quote = quotes.getFirst();
        assertThat(quote.exchange()).isEqualTo(Exchange.NSE);
        assertThat(quote.tradingSymbol()).isEqualTo("SBIN-EQ");
        assertThat(quote.lastTradedPrice()).isEqualByComparingTo("800.25");
        assertThat(quote.week52High()).isEqualByComparingTo("900.00");
        assertThat(quote.depth().buy()).hasSize(1);
        assertThat(quote.depth().buy().getFirst().price()).isEqualByComparingTo("800.20");
        assertThat(quote.depth().sell().getFirst().orders()).isEqualTo(3L);
        server.verify();
    }

    @Test
    void renewsOnceAndRetriesAfterABrokerSessionRejection() {
        when(sessionManager.accessToken()).thenReturn("expired-jwt", "fresh-jwt");
        server.expect(requestTo(QUOTE_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"status":false,"message":"Token Expired","errorcode":"AG8002","data":null}
                                """));
        server.expect(requestTo(QUOTE_URL))
                .andRespond(withSuccess("""
                        {"status":true,"message":"SUCCESS","errorcode":"","data":{"fetched":[],"unfetched":[]}}
                        """, MediaType.APPLICATION_JSON));

        List<Quote> quotes = provider.quotes(QuoteMode.LTP, Map.of(Exchange.NSE, List.of("3045")));

        assertThat(quotes).isEmpty();
        verify(sessionManager).forceRenew();
        server.verify();
    }

    @Test
    void mapsHistoricalCandleRows() {
        server.expect(requestTo(CANDLE_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "status": true,
                          "message": "SUCCESS",
                          "errorcode": "",
                          "data": [
                            ["2023-09-06T11:15:00+05:30", 19571.2, 19573.35, 19534.4, 19552.05, 0]
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        CandleRequest request = new CandleRequest(
                Exchange.NSE,
                "3045",
                CandleInterval.ONE_MINUTE,
                LocalDateTime.of(2023, 9, 6, 11, 0),
                LocalDateTime.of(2023, 9, 6, 12, 0));
        List<Candle> candles = provider.candles(request);

        assertThat(candles).hasSize(1);
        Candle candle = candles.getFirst();
        assertThat(candle.timestamp()).isEqualTo(OffsetDateTime.parse("2023-09-06T11:15:00+05:30"));
        assertThat(candle.high()).isEqualByComparingTo("19573.35");
        assertThat(candle.volume()).isZero();
        server.verify();
    }

    @Test
    void downloadsAndMapsInstrumentMaster() {
        server.expect(requestTo(INSTRUMENT_URL))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "token": "3045",
                          "symbol": "SBIN-EQ",
                          "name": "SBIN",
                          "expiry": "",
                          "strike": "0.000000",
                          "tick_size": "0.05",
                          "lotsize": "1",
                          "instrumenttype": "EQ",
                          "exch_seg": "NSE",
                          "freeze_qty": "100.00"
                        }]
                        """, MediaType.APPLICATION_JSON));

        InstrumentMaster master = provider.instruments();

        assertThat(master.size()).isEqualTo(1);
        Instrument instrument = master.instruments().getFirst();
        assertThat(instrument.symbolToken()).isEqualTo("3045");
        assertThat(instrument.tradingSymbol()).isEqualTo("SBIN-EQ");
        assertThat(instrument.lotSize()).isEqualTo(1L);
        assertThat(instrument.freezeQuantity()).isEqualByComparingTo("100.00");
        assertThat(master.retrievedAt()).isEqualTo(clock.instant());
        server.verify();
    }

    @Test
    void rejectsQuoteRequestsAboveTheDocumentedSymbolLimit() {
        Map<Exchange, List<String>> tokens = Map.of(
                Exchange.NSE,
                IntStream.range(0, 51).mapToObj(String::valueOf).toList());

        assertThatThrownBy(() -> provider.quotes(QuoteMode.LTP, tokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("50");
    }

    @Test
    void rejectsCandleSpansAboveTheDocumentedMaximum() {
        assertThatThrownBy(() -> new CandleRequest(
                Exchange.NSE,
                "3045",
                CandleInterval.ONE_MINUTE,
                LocalDateTime.of(2023, 1, 1, 9, 0),
                LocalDateTime.of(2023, 2, 1, 9, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
    }
}
