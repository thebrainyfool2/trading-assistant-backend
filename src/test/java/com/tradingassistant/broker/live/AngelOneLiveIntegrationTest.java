package com.tradingassistant.broker.live;

import com.tradingassistant.broker.api.BrokerProvider;
import com.tradingassistant.broker.api.MarketFeedConnection;
import com.tradingassistant.broker.api.MarketFeedListener;
import com.tradingassistant.broker.model.BrokerProfile;
import com.tradingassistant.broker.model.CandleInterval;
import com.tradingassistant.broker.model.CandleRequest;
import com.tradingassistant.broker.model.Exchange;
import com.tradingassistant.broker.model.FeedMode;
import com.tradingassistant.broker.model.FeedSubscription;
import com.tradingassistant.broker.model.InstrumentMaster;
import com.tradingassistant.broker.model.Quote;
import com.tradingassistant.broker.model.QuoteMode;
import com.tradingassistant.broker.model.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in live verification. Run only with real credentials in the environment:
 *
 * <pre>
 * SMARTAPI_LIVE_TESTS=true SMARTAPI_API_KEY=... SMARTAPI_CLIENT_CODE=... \
 * SMARTAPI_PIN=... SMARTAPI_TOTP_SECRET=... \
 * SMARTAPI_CLIENT_LOCAL_IP=... SMARTAPI_CLIENT_PUBLIC_IP=... \
 * SMARTAPI_MAC_ADDRESS=... \
 * mvn -Dtest=AngelOneLiveIntegrationTest test
 * </pre>
 *
 * The test never prints tokens, OTPs, PINs or API keys.
 */
@SpringBootTest(properties = "app.broker.angel-one.enabled=true")
@EnabledIfEnvironmentVariable(named = "SMARTAPI_LIVE_TESTS", matches = "true")
class AngelOneLiveIntegrationTest {

    @Autowired
    private BrokerProvider broker;

    @Test
    void verifiesAuthenticationProfileQuotesCandlesInstrumentsAndFeed() throws Exception {
        SessionStatus session = broker.authenticate();
        assertThat(session.authenticated()).isTrue();
        assertThat(session.expiresAt()).isNotNull();

        BrokerProfile profile = broker.profile();
        assertThat(profile.clientCode()).isNotBlank();

        List<Quote> quotes = broker.quotes(QuoteMode.FULL, Map.of(Exchange.NSE, List.of("3045")));
        assertThat(quotes).isNotEmpty();

        List<com.tradingassistant.broker.model.Candle> candles = broker.candles(new CandleRequest(
                Exchange.NSE,
                "3045",
                CandleInterval.ONE_DAY,
                LocalDateTime.of(2024, 1, 1, 9, 15),
                LocalDateTime.of(2024, 1, 10, 15, 30)));
        assertThat(candles).isNotEmpty();

        InstrumentMaster instruments = broker.instruments();
        assertThat(instruments.size()).isGreaterThan(1000);

        CountDownLatch connected = new CountDownLatch(1);
        MarketFeedListener listener = new MarketFeedListener() {
            @Override
            public void onConnected() {
                connected.countDown();
            }
        };
        try (MarketFeedConnection connection = broker.openMarketFeed(
                new FeedSubscription(FeedMode.LTP, Map.of(Exchange.NSE, List.of("3045"))),
                listener)) {
            assertThat(connection.isOpen()).isTrue();
            assertThat(connected.await(15, TimeUnit.SECONDS)).isTrue();
        }
    }
}
