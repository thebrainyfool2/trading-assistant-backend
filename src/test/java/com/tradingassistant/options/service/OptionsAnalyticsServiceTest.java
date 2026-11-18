package com.tradingassistant.options.service;

import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.OptionContract;
import com.tradingassistant.market.domain.Quote;
import com.tradingassistant.options.OptionsTestFixtures;
import com.tradingassistant.options.chain.OptionChainBuilder;
import com.tradingassistant.options.config.OptionsAnalyticsConfig;
import com.tradingassistant.options.domain.OptionsAssessment;
import com.tradingassistant.options.engine.OptionsAnalyticsEngine;
import com.tradingassistant.options.exception.OptionsValidationException;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventType;
import com.tradingassistant.pipeline.event.OptionsAssessmentGeneratedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionsAnalyticsServiceTest {

    private final OptionsAnalyticsConfig config = OptionsTestFixtures.CONFIG;
    private InMemoryOptionChainProvider provider;
    private List<MarketEvent> published;
    private OptionsAnalyticsService service;

    @BeforeEach
    void setUp() {
        provider = new InMemoryOptionChainProvider(new OptionChainBuilder(config));
        published = new ArrayList<>();
        service = new OptionsAnalyticsService(OptionsAnalyticsEngine.standard(config), config,
                provider, published::add, Clock.fixed(OptionsTestFixtures.START, ZoneOffset.UTC));

        register(OptionsTestFixtures.SPOT, OptionsTestFixtures.UNDERLYING);
        register(OptionsTestFixtures.OTHER_SPOT, OptionsTestFixtures.OTHER_UNDERLYING);
    }

    private void register(InstrumentKey spot, String underlying) {
        List<OptionContract> contracts = OptionsTestFixtures.ladder(underlying,
                OptionsTestFixtures.EXPIRY, OptionsTestFixtures.strikes("480", "520", "10"));
        Map<InstrumentKey, Quote> quotes = OptionsTestFixtures.quoted(contracts, "500", "0.25",
                1_000L, 500L, "0.05", "0.10");
        provider.registerUnderlying(spot, underlying);
        provider.updateContracts(underlying, contracts);
        provider.updateQuotes(quotes.values());
        provider.updateSpot(spot, new java.math.BigDecimal("500"));
    }

    private void publish(InstrumentKey instrument) {
        MarketAssessment market = OptionsTestFixtures.marketAssessment(instrument,
                OptionsTestFixtures.START);
        service.onEvent(new MarketAssessmentGeneratedEvent(market, OptionsTestFixtures.START));
    }

    @Test
    void publishesAnAssessmentForARegisteredInstrument() {
        publish(OptionsTestFixtures.SPOT);

        assertThat(published).hasSize(1);
        assertThat(published.get(0)).isInstanceOf(OptionsAssessmentGeneratedEvent.class);
        OptionsAssessmentGeneratedEvent event = (OptionsAssessmentGeneratedEvent) published.get(0);
        assertThat(event.type()).isEqualTo(MarketEventType.OPTIONS_ASSESSMENT_GENERATED);
        assertThat(event.occurredAt()).isEqualTo(OptionsTestFixtures.START);
        assertThat(event.assessment().underlying()).isEqualTo(OptionsTestFixtures.UNDERLYING);
        assertThat(event.assessment().spotInstrument()).isEqualTo(OptionsTestFixtures.SPOT);
    }

    @Test
    void ignoresInstrumentsWithoutARegisteredChain() {
        publish(new InstrumentKey(com.tradingassistant.market.domain.Segment.NSE_CASH, "9999"));

        assertThat(published).isEmpty();
        assertThat(service.stateCount()).isZero();
    }

    @Test
    void ignoresEventsOtherThanMarketAssessments() {
        publish(OptionsTestFixtures.SPOT);
        MarketEvent event = new OptionsAssessmentGeneratedEvent(
                ((OptionsAssessmentGeneratedEvent) published.get(0)).assessment(),
                OptionsTestFixtures.START);

        service.onEvent(event);

        assertThat(published).hasSize(1);
        assertThat(service.stateCount()).isEqualTo(1);
    }

    @Test
    void keepsVolatilityAndChainHistoryBetweenCaptures() {
        publish(OptionsTestFixtures.SPOT);
        publish(OptionsTestFixtures.SPOT);

        assertThat(service.stateCount()).isEqualTo(1);
        assertThat(service.volatilityHistory(OptionsTestFixtures.SPOT)).isPresent();
        assertThat(service.volatilityHistory(OptionsTestFixtures.SPOT).orElseThrow().size())
                .isEqualTo(1);
        assertThat(service.previousChain(OptionsTestFixtures.SPOT)).isPresent();
        OptionsAssessment second = ((OptionsAssessmentGeneratedEvent) published.get(1))
                .assessment();
        assertThat(second.openInterest().status().name()).isNotEqualTo("INSUFFICIENT_DATA");
        assertThat(second.diagnostics()).containsEntry("previousLegs", "10");
    }

    @Test
    void isolatesUnderlyings() {
        publish(OptionsTestFixtures.SPOT);
        publish(OptionsTestFixtures.OTHER_SPOT);

        assertThat(published).hasSize(2);
        assertThat(service.stateCount()).isEqualTo(2);
        assertThat(((OptionsAssessmentGeneratedEvent) published.get(0)).assessment().underlying())
                .isEqualTo(OptionsTestFixtures.UNDERLYING);
        assertThat(((OptionsAssessmentGeneratedEvent) published.get(1)).assessment().underlying())
                .isEqualTo(OptionsTestFixtures.OTHER_UNDERLYING);
    }

    @Test
    void replaysTheSameCaptureIdempotently() {
        publish(OptionsTestFixtures.SPOT);
        publish(OptionsTestFixtures.SPOT);
        publish(OptionsTestFixtures.SPOT);

        OptionsAssessment second = ((OptionsAssessmentGeneratedEvent) published.get(1))
                .assessment();
        OptionsAssessment third = ((OptionsAssessmentGeneratedEvent) published.get(2))
                .assessment();
        assertThat(published).hasSize(3);
        assertThat(third).isEqualTo(second);
        assertThat(third.openInterest().strikes()).allSatisfy(strike -> {
            assertThat(strike.callOpenInterestChange()).contains(0L);
            assertThat(strike.putOpenInterestChange()).contains(0L);
        });
    }

    @Test
    void dropsStateOnClearAndRemove() {
        publish(OptionsTestFixtures.SPOT);
        publish(OptionsTestFixtures.OTHER_SPOT);

        assertThat(service.remove(OptionsTestFixtures.SPOT)).isTrue();
        assertThat(service.stateCount()).isEqualTo(1);

        service.clear();

        assertThat(service.stateCount()).isZero();
        assertThat(service.previousChain(OptionsTestFixtures.OTHER_SPOT)).isEmpty();
    }

    @Test
    void rejectsANullEvent() {
        assertThatThrownBy(() -> service.onEvent(null))
                .isInstanceOf(OptionsValidationException.class)
                .hasMessageContaining("Event must not be null");
    }
}
