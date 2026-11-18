package com.tradingassistant.intelligence.domain;

import com.tradingassistant.indicator.domain.IndicatorResult;
import com.tradingassistant.indicator.domain.IndicatorSeries;
import com.tradingassistant.intelligence.IntelligenceTestFixtures;
import com.tradingassistant.intelligence.exception.IntelligenceValidationException;
import com.tradingassistant.market.domain.Candle;
import com.tradingassistant.market.domain.Interval;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.tradingassistant.intelligence.IntelligenceTestFixtures.KEY;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.calendar;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.candle;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.result;
import static com.tradingassistant.intelligence.IntelligenceTestFixtures.timestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisContextTest {

    @Test
    void exposesTheLatestCandleAndIndicatorResults() {
        Candle candle = candle(3, "110");
        Instant now = candle.timestamp();
        Map<String, IndicatorSeries> indicators = Map.of(
                "SMA", IntelligenceTestFixtures.series("SMA", List.of(result("SMA", now, "value", "105"))),
                "EMA", IntelligenceTestFixtures.series("EMA", List.of(result("EMA", now, "value", "106"))));

        AnalysisContext context = new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), indicators, calendar());

        assertThat(context.latestCandle()).isEqualTo(candle);
        assertThat(context.candleCount()).isEqualTo(1);
        assertThat(context.latestResults().keySet()).containsExactlyInAnyOrder("SMA", "EMA");
        assertThat(context.series("sma")).isPresent();
        assertThat(context.series(" ")).isEmpty();
        assertThat(context.series(null)).isEmpty();
    }

    @Test
    void canonicalizesIndicatorKeys() {
        Candle candle = candle(0, "100");
        Map<String, IndicatorSeries> indicators = Map.of(
                "sma", IntelligenceTestFixtures.series("SMA", List.of(result("SMA", candle.timestamp(), "value", "99"))));

        AnalysisContext context = new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), indicators, calendar());

        assertThat(context.indicators()).containsKey("SMA");
        assertThat(context.latestResults()).containsKey("SMA");
    }

    @Test
    void rejectsNullAndEmptyInputs() {
        Candle candle = candle(0, "100");
        Map<String, IndicatorSeries> indicators = Map.of(
                "SMA", IntelligenceTestFixtures.series("SMA", List.of(result("SMA", candle.timestamp(), "value", "99"))));

        assertThatThrownBy(() -> new AnalysisContext(null, Interval.ONE_MINUTE, List.of(candle), indicators, calendar()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AnalysisContext(KEY, null, List.of(candle), indicators, calendar()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AnalysisContext(KEY, Interval.ONE_MINUTE, List.of(candle), indicators, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AnalysisContext(KEY, Interval.ONE_MINUTE, List.of(), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class);
        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), Map.of(), calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("missing indicators");
        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), null, calendar()))
                .isInstanceOf(IntelligenceValidationException.class);
    }

    @Test
    void rejectsMixedInstrumentAndIntervalCandles() {
        Candle first = candle(0, "100");
        Candle otherInstrument = new Candle(
                IntelligenceTestFixtures.OTHER_KEY, Interval.ONE_MINUTE, timestamp(1),
                first.open(), first.high(), first.low(), first.close(), 10L);
        Candle otherInterval = new Candle(
                KEY, Interval.FIVE_MINUTE, timestamp(1),
                first.open(), first.high(), first.low(), first.close(), 10L);
        Map<String, IndicatorSeries> indicators = Map.of(
                "SMA", IntelligenceTestFixtures.series("SMA",
                        List.of(result("SMA", timestamp(1), "value", "99"))));

        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(first, otherInstrument), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("instrument");
        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(first, otherInterval), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void rejectsNonAscendingCandles() {
        Candle later = candle(1, "101");
        Candle earlier = candle(0, "100");
        Map<String, IndicatorSeries> indicators = Map.of(
                "SMA", IntelligenceTestFixtures.series("SMA", List.of(result("SMA", earlier.timestamp(), "value", "99"))));

        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(later, earlier), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("ascending");
    }

    @Test
    void rejectsDuplicateIndicatorTypesIgnoringCase() {
        Candle candle = candle(0, "100");
        Map<String, IndicatorSeries> indicators = new LinkedHashMap<>();
        indicators.put("SMA", IntelligenceTestFixtures.series("SMA",
                List.of(result("SMA", candle.timestamp(), "value", "99"))));
        indicators.put("sma", IntelligenceTestFixtures.series("SMA",
                List.of(result("SMA", candle.timestamp(), "value", "99"))));

        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("Duplicate indicator type");
    }

    @Test
    void rejectsSeriesWhoseNameContradictsItsKey() {
        Candle candle = candle(0, "100");
        Map<String, IndicatorSeries> indicators = Map.of(
                "SMA", IntelligenceTestFixtures.series("EMA",
                        List.of(result("EMA", candle.timestamp(), "value", "99"))));

        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("does not match its key");
    }

    @Test
    void rejectsIndicatorsThatAreNotAlignedToTheLatestCandle() {
        Candle candle = candle(0, "100");
        Map<String, IndicatorSeries> indicators = Map.of(
                "SMA", IntelligenceTestFixtures.series("SMA",
                        List.of(result("SMA", timestamp(-1), "value", "99"))));

        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), indicators, calendar()))
                .isInstanceOf(IntelligenceValidationException.class)
                .hasMessageContaining("not aligned");
    }

    @Test
    void rejectsBlankKeysNullSeriesAndEmptySeries() {
        Candle candle = candle(0, "100");
        IndicatorResult valid = result("SMA", candle.timestamp(), "value", "99");

        Map<String, IndicatorSeries> blankKey = new HashMap<>();
        blankKey.put(" ", IntelligenceTestFixtures.series("SMA", List.of(valid)));
        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), blankKey, calendar()))
                .isInstanceOf(IntelligenceValidationException.class);

        Map<String, IndicatorSeries> nullSeries = new HashMap<>();
        nullSeries.put("SMA", null);
        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), nullSeries, calendar()))
                .isInstanceOf(IntelligenceValidationException.class);

        Map<String, IndicatorSeries> emptySeries = Map.of(
                "SMA", new IndicatorSeries("SMA", "1.0", List.of()));
        assertThatThrownBy(() -> new AnalysisContext(
                KEY, Interval.ONE_MINUTE, List.of(candle), emptySeries, calendar()))
                .isInstanceOf(IntelligenceValidationException.class);
    }
}
