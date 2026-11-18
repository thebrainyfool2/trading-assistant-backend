package com.tradingassistant.intelligence.service;

import com.tradingassistant.indicator.api.Indicator;
import com.tradingassistant.indicator.config.IndicatorConfig;
import com.tradingassistant.indicator.config.PriceSource;
import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.engine.IndicatorEngine;
import com.tradingassistant.intelligence.IntelligenceTestFixtures;
import com.tradingassistant.intelligence.config.MarketIntelligenceConfig;
import com.tradingassistant.intelligence.domain.MarketAssessment;
import com.tradingassistant.intelligence.engine.MarketIntelligenceEngine;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import com.tradingassistant.intelligence.session.AnalysisKey;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import com.tradingassistant.market.domain.InstrumentKey;
import com.tradingassistant.market.domain.Segment;
import com.tradingassistant.pipeline.calendar.MarketCalendarRegistry;
import com.tradingassistant.pipeline.event.MarketAssessmentGeneratedEvent;
import com.tradingassistant.pipeline.event.MarketEvent;
import com.tradingassistant.pipeline.event.MarketEventBus;
import com.tradingassistant.pipeline.event.SessionExpiredEvent;
import com.tradingassistant.pipeline.session.IndicatorSessionKey;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.KEY;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.OTHER_KEY;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.calendar;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candle;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MarketIntelligenceServiceTest {

    private static final Instant NOW = IntelligenceTestFixtures.ist("2026-11-18T10:00:00");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void publishesAnAssessmentForEachIndicatorUpdate() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());
        Candle candle = candle(0, "100");

        harness.service.onEvent(update(candle, Map.of(
                "SMA", result("SMA", candle.timestamp(), "value", "99"))));

        List<MarketAssessment> assessments = harness.assessments();
        assertThat(assessments).hasSize(1);
        MarketAssessment assessment = assessments.getFirst();
        assertThat(assessment.instrument()).isEqualTo(KEY);
        assertThat(assessment.interval()).isEqualTo(Interval.ONE_MINUTE);
        assertThat(assessment.timestamp()).isEqualTo(candle.timestamp());
        assertThat(harness.events.getFirst().type())
                .isEqualTo(com.tradingassistant.pipeline.event.MarketEventType.MARKET_ASSESSMENT_GENERATED);
    }

    @Test
    void ignoresEveryOtherEvent() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());

        harness.service.onEvent(new SessionExpiredEvent(
                new IndicatorSessionKey(KEY, Interval.ONE_MINUTE, IndicatorConfig.defaults()),
                "idle", NOW));

        assertThat(harness.assessments()).isEmpty();
        assertThat(harness.service.historyCount()).isZero();
    }

    @Test
    void rejectsANullEvent() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());

        assertThatThrownBy(() -> harness.service.onEvent(null))
                .isInstanceOf(IntelligenceValidationException.class);
    }

    @Test
    void keepsOnlyTheConfiguredHistoryWindow() {
        MarketIntelligenceConfig config = MarketIntelligenceConfig.builder()
                .structureLookback(4)
                .historyWindow(10)
                .build();
        Harness harness = new Harness(config);

        for (int minute = 0; minute < 25; minute++) {
            Candle candle = candle(minute, String.valueOf(100 + minute));
            harness.service.onEvent(update(candle, Map.of(
                    "SMA", result("SMA", candle.timestamp(), "value", "100"))));
        }

        assertThat(harness.service.historyCount()).isEqualTo(1);
        assertThat(harness.service.history(key()).orElseThrow().candleCount()).isEqualTo(10);
        assertThat(harness.service.history(key()).orElseThrow().indicatorTypes()).containsExactly("SMA");
        assertThat(harness.assessments()).hasSize(25);
    }

    @Test
    void rejectsOutOfOrderAndDuplicateCandles() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());
        Candle later = candle(1, "101");
        harness.service.onEvent(update(later, Map.of(
                "SMA", result("SMA", later.timestamp(), "value", "100"))));

        Candle earlier = candle(0, "100");
        assertThatThrownBy(() -> harness.service.onEvent(update(earlier, Map.of(
                "SMA", result("SMA", earlier.timestamp(), "value", "100")))))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("increase");
        assertThatThrownBy(() -> harness.service.onEvent(update(later, Map.of(
                "SMA", result("SMA", later.timestamp(), "value", "100")))))
                .isInstanceOf(IntelligenceValidationException.class);
    }

    @Test
    void rejectsAnIndicatorResultThatIsNotAlignedToTheCandle() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());
        Candle candle = candle(0, "100");

        assertThatThrownBy(() -> harness.service.onEvent(update(candle, Map.of(
                "SMA", result("SMA", timestamp(5), "value", "100")))))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("aligned");
    }

    @Test
    void isolatesHistoriesPerInstrument() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());
        Candle first = candle(0, "100");
        Candle second = candleFor(OTHER_KEY, 0, "500");

        harness.service.onEvent(update(first, Map.of(
                "SMA", result("SMA", first.timestamp(), "value", "100"))));
        harness.service.onEvent(update(second, Map.of(
                "SMA", result("SMA", second.timestamp(), "value", "500"))));

        assertThat(harness.service.historyCount()).isEqualTo(2);
        assertThat(harness.service.keys()).containsExactlyInAnyOrder(
                key(), new AnalysisKey(OTHER_KEY, Interval.ONE_MINUTE));
        assertThat(harness.assessments()).extracting(MarketAssessment::instrument)
                .containsExactlyInAnyOrder(KEY, OTHER_KEY);
    }

    @Test
    void replayMatchesDirectEvaluationOfTheSameHistory() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());
        feedRealIndicators(harness, KEY, 40);

        MarketAssessment last = harness.assessments().getLast();
        var history = harness.service.history(key()).orElseThrow();
        MarketAssessment direct = harness.engine.assess(history.snapshot(calendar()));

        assertThat(last).isEqualTo(direct);
    }

    @Test
    void largeMultiInstrumentReplayStaysWithinABound() {
        Harness harness = new Harness(MarketIntelligenceConfig.builder()
                .structureLookback(20)
                .historyWindow(120)
                .build());
        List<InstrumentKey> keys = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            keys.add(new InstrumentKey(Segment.NSE_CASH, String.valueOf(4000 + index)));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            for (InstrumentKey instrument : keys) {
                feedRealIndicators(harness, instrument, 500);
            }
        });

        assertThat(harness.service.historyCount()).isEqualTo(keys.size());
        assertThat(harness.assessments()).hasSize(keys.size() * 500);
        assertThat(harness.assessments().getLast().status()).isNotNull();
    }

    @Test
    void historiesCanBeRemovedAndCleared() {
        Harness harness = new Harness(MarketIntelligenceConfig.defaults());
        Candle candle = candle(0, "100");
        harness.service.onEvent(update(candle, Map.of(
                "SMA", result("SMA", candle.timestamp(), "value", "100"))));

        assertThat(harness.service.remove(key())).isTrue();
        assertThat(harness.service.remove(key())).isFalse();
        assertThat(harness.service.remove(null)).isFalse();
        assertThat(harness.service.history(null)).isEmpty();

        harness.service.onEvent(update(candle, Map.of(
                "SMA", result("SMA", candle.timestamp(), "value", "100"))));
        harness.service.clear();
        assertThat(harness.service.historyCount()).isZero();
        assertThat(harness.service.config()).isEqualTo(harness.config);
    }

    private static void feedRealIndicators(Harness harness, InstrumentKey key, int count) {
        Indicator sma = IndicatorEngine.standard().create(
                "SMA", IndicatorConfig.period(5, PriceSource.CLOSE));
        Indicator atr = IndicatorEngine.standard().create(
                "ATR", IndicatorConfig.period(3, PriceSource.CLOSE));
        for (int minute = 0; minute < count; minute++) {
            Candle candle = candleFor(key, minute, String.valueOf(100 + (minute % 17)));
            Map<String, IndicatorResult> results = new LinkedHashMap<>();
            results.put("SMA", sma.update(candle));
            results.put("ATR", atr.update(candle));
            harness.service.onEvent(update(candle, results));
        }
    }

    private static com.tradingassistant.pipeline.event.IndicatorUpdatedEvent update(
            Candle candle, Map<String, IndicatorResult> results) {
        return new com.tradingassistant.pipeline.event.IndicatorUpdatedEvent(
                new IndicatorSessionKey(candle.instrument(), candle.interval(), IndicatorConfig.defaults()),
                candle,
                results,
                NOW);
    }

    private static Candle candleFor(InstrumentKey instrument, int minute, String close) {
        BigDecimal value = new BigDecimal(close);
        return new Candle(
                instrument,
                Interval.ONE_MINUTE,
                timestamp(minute),
                value,
                value.add(BigDecimal.ONE),
                value.subtract(BigDecimal.ONE),
                value,
                1_000L);
    }

    private static AnalysisKey key() {
        return new AnalysisKey(KEY, Interval.ONE_MINUTE);
    }

    private static final class Harness {

        private final List<MarketEvent> events = Collections.synchronizedList(new ArrayList<>());
        private final MarketEventBus bus = new MarketEventBus();
        private final MarketIntelligenceConfig config;
        private final MarketIntelligenceEngine engine;
        private final MarketIntelligenceService service;

        Harness(MarketIntelligenceConfig config) {
            this.config = config;
            this.engine = MarketIntelligenceEngine.standard(config);
            this.bus.subscribe(events::add);
            this.service = new MarketIntelligenceService(
                    engine, config, MarketCalendarRegistry.defaults(), bus, CLOCK);
        }

        List<MarketAssessment> assessments() {
            return events.stream()
                    .filter(MarketAssessmentGeneratedEvent.class::isInstance)
                    .map(MarketAssessmentGeneratedEvent.class::cast)
                    .map(MarketAssessmentGeneratedEvent::assessment)
                    .toList();
        }
    }
}
