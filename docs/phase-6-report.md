# Phase 6 — Market Intelligence Engine

**Status: implemented, tested and documented. Awaiting architectural review.**
No Options Analytics, Greeks, implied volatility, PCR, Max Pain, risk, strategy, trade qualification, position sizing, order handling, persistence, database, scheduler, AI, machine learning or notification functionality was started.

---

## 1. Executive Summary

Phases 2–5 produced a broker adapter, a market domain, sixteen indicators and a runtime pipeline that publishes `IndicatorUpdatedEvent` for every completed candle. Nothing yet described what those numbers meant.

Phase 6 adds that description layer and nothing more. It consumes completed candles, indicator results and market-session context, and produces one immutable `MarketAssessment` per candle:

```text
IndicatorUpdatedEvent
        │
        ▼
MarketIntelligenceHistory        rolling candles + indicator series per instrument/interval
        │
        ▼
AnalysisContext                  validated immutable input
        │
        ├── TrendAnalyzer
        ├── MomentumAnalyzer
        ├── VolatilityAnalyzer
        ├── ParticipationAnalyzer
        ├── StructureAnalyzer
        └── SessionContextAnalyzer
        │
        ▼
MarketIntelligenceEngine         composition + overall status + confidence
        │
        ▼
MarketAssessmentGeneratedEvent
```

The engine describes; it never prescribes. There is no score, no BUY, no SELL, no LONG, no SHORT, no confidence percentage, no risk measure, no option data and no order concept anywhere in the model or the analysis code.

Verification: `mvn -B -ntp verify` → **295 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **83 tests are new in Phase 6**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3.

---

## 2. Intelligence Architecture

`com.tradingassistant.intelligence` (35 main classes, 12 test classes):

| Package | Contents |
|---|---|
| `intelligence.api` | `MarketAnalyzer<R>` extension contract |
| `intelligence.config` | immutable `MarketIntelligenceConfig` with builder and validation, `WarmupPolicy` |
| `intelligence.domain` | `AnalysisContext`, `MarketAssessment`, six aspect assessments, their state enums, `AssessmentStatus`, `Confidence` |
| `intelligence.analysis` | six analyzers plus shared `IndicatorInputs` lookup rules |
| `intelligence.engine` | `MarketIntelligenceEngine` composition |
| `intelligence.session` | `AnalysisKey`, bounded `MarketIntelligenceHistory` |
| `intelligence.service` | `MarketIntelligenceService` event adapter |
| `intelligence.exception` | validation failures |
| `intelligence` | `IntelligenceConfiguration` conditional wiring |

Dependency direction:

- analyzers depend on the domain model, configuration and the Phase 4 indicator value keys only;
- the engine depends on the analyzer contract, never the reverse;
- the service depends on the Phase 5 event contract, the calendar registry and the engine;
- no analyzer reads the pipeline, the broker adapter, HTTP objects or another analyzer.

`AnalysisContext` is the only way data enters an analyzer, so a new analyzer cannot invent an input channel.

---

## 3. Assessment Model

`MarketAssessment` is a record with named fields:

| Field | Meaning |
|---|---|
| `instrument`, `interval`, `timestamp` | which market and candle is described |
| `status` | `READY`, `PARTIAL`, `INSUFFICIENT_DATA` or `UNKNOWN` |
| `warmup` | true when any market aspect is not fully ready |
| `confidence` | `Confidence(level, basis)` — `HIGH`, `MEDIUM`, `LOW`, `UNKNOWN` plus descriptive basis |
| `trend` | `TrendAssessment(state, status, indicators, summary)` |
| `momentum` | `MomentumAssessment(state, status, indicators, summary)` |
| `volatility` | `VolatilityAssessment(state, status, indicators, summary)` |
| `participation` | `ParticipationAssessment(state, status, indicators, summary)` |
| `structure` | `StructureAssessment(bias, signals, status, summary)` |
| `session` | `SessionAssessment(phase, status, Optional<TradingSession>, summary)` |

States:

- trend: `BULLISH`, `BEARISH`, `SIDEWAYS`, `UNKNOWN`
- momentum: `STRENGTHENING`, `WEAKENING`, `NEUTRAL`, `UNKNOWN`
- volatility: `EXPANDING`, `CONTRACTING`, `STABLE`, `UNKNOWN`
- participation: `STRONG`, `AVERAGE`, `WEAK`, `UNKNOWN`
- structure bias: `UPTREND`, `DOWNTREND`, `RANGING`, `UNKNOWN`
- structure signals: `HIGHER_HIGH`, `HIGHER_LOW`, `LOWER_HIGH`, `LOWER_LOW`, `RANGE`, `BREAKOUT_CANDIDATE`, `BREAKDOWN_CANDIDATE`
- session phase: `PRE_OPEN`, `OPENING`, `REGULAR`, `CLOSING`, `AFTER_MARKET`, `UNKNOWN`

There are no arrays, no positional value tuples and no numeric scores. Every collection is an immutable copy; every state is an enum. `Confidence.basis` is text such as `"5 of 5 market aspects ready"`, which is diagnostic metadata rather than a score.

---

## 4. Analyzer Design

Each analyzer implements `MarketAnalyzer<R>` with `name()` and `analyze(context, config)`. An analyzer reads only the validated context, keeps no state and never calls another analyzer; composition exists only in the engine.

**Trend** — three price-based inputs vote: close versus EMA, close versus SMA, and Supertrend `direction`. ADX is a strength gate, never a direction. Direction requires `trendAgreementVotes` net votes and, when `requireAdxForDirection` is true, `ADX >= trendAdxThreshold`. A configured-but-missing ADX yields `UNKNOWN`, not a guess; weak ADX yields `SIDEWAYS`. No single indicator can decide the trend unless the configuration requires one vote.

**Momentum** — RSI against configurable upper/lower bands, the MACD histogram against zero, and `%K` against `%D`. Votes aggregate to strengthening, weakening or neutral. A tie is neutral, which is a description rather than a signal.

**Volatility** — expansion is always a comparison, never a level. ATR uses its value; Bollinger, Keltner and Donchian use upper-minus-lower width. Each input compares its current width with its most recent earlier width and votes when the relative change exceeds `volatilityChangeThreshold`. A zero previous width is not comparable and is skipped rather than divided by zero.

**Participation** — on-balance volume contributes the direction of its change, Chaikin money flow contributes its signed level against `participationCmfThreshold`, and close versus session VWAP contributes whether trading is above or below the session's volume-weighted price. The result is strong, average or weak participation, which is not a liquidity or sizing judgement.

**Structure** — candle geometry only. The lookback window is split into an older and a newer half; their highest highs and lowest lows are compared, so higher/lower highs and lows are window statements rather than subjective pivot labels. Both comparisons inside `structureRangeTolerancePercent` produce `RANGE`. A close beyond every earlier high or low is a `BREAKOUT_CANDIDATE` or `BREAKDOWN_CANDIDATE` — geometry, never advice.

**Session** — the calendar alone decides trading state; no weekday or clock time is hardcoded. Opening and closing phases are configured durations measured from the resolved session boundaries, so a half day is described correctly without a special case. Outside a session the phase is `PRE_OPEN` when a session opens later on the same market date, otherwise `AFTER_MARKET` (which covers weekends and holidays).

---

## 5. Configuration

`MarketIntelligenceConfig` is an immutable record with a construction-time builder and full validation:

| Knob | Default | Purpose |
|---|---|---|
| `trendAgreementVotes` | 2 | directional inputs that must agree |
| `trendAdxThreshold` | 25 | ADX level that confirms strength |
| `requireAdxForDirection` | true | refuse a direction without ADX confirmation |
| `momentumLowerThreshold` / `momentumUpperThreshold` | 40 / 60 | oscillator neutral band |
| `volatilityChangeThreshold` | 0.05 | relative width change that counts as movement |
| `participationCmfThreshold` | 0.05 | absolute CMF level that counts as strong or weak |
| `structureLookback` | 20 | candles used for structure |
| `structureRangeTolerancePercent` | 0.5 | allowed drift that still counts as a range |
| `historyWindow` | 120 | candles and results retained per instrument and interval |
| `openingWindow` / `closingWindow` | 15 min / 15 min | session phase durations |
| `warmupPolicy` | `PERMISSIVE` | behaviour for incomplete inputs |
| `highConfidenceAspectCount` / `mediumConfidenceAspectCount` | 4 / 2 | readiness needed for confidence levels |
| `requiredIndicators` | empty | indicator types every context must contain |

Validation rejects out-of-range votes, inverted or impossible momentum bands, negative thresholds, a lookback below four candles, a history window smaller than the lookback, non-positive session windows, inverted or impossible confidence thresholds, and blank required types. There is no runtime mutation: changing a threshold means constructing a new configuration, so an assessment remains reproducible for a given configuration.

---

## 6. Warm-up Policy

Indicators publish warm-up observations, and Phase 6 never assumes a missing input. `AssessmentStatus` is reported per aspect and for the whole assessment:

- `READY` — every expected input was usable;
- `PARTIAL` — some inputs were usable, some were missing or warming up;
- `INSUFFICIENT_DATA` — no input was usable, so the aspect is `UNKNOWN`;
- `UNKNOWN` — inputs were usable but a conclusion could not be reached.

`WarmupPolicy` selects the deterministic reaction:

- `PERMISSIVE` (default) describes the aspect with the inputs that are ready and marks it `PARTIAL`;
- `STRICT` returns `UNKNOWN` for any aspect that is not fully ready while still reporting which inputs existed.

An indicator observation is usable only when it is present, not flagged `warmup`, and contains the named value the rule needs. The previous observation is the most recent earlier observation satisfying the same rule, so a warm-up row neither hides history nor becomes a fake baseline.

Overall status: `READY` only when every market aspect and the session context are ready; `INSUFFICIENT_DATA` when no market aspect could be described (knowing the session alone is not a description of the market); otherwise `PARTIAL`. Confidence counts only the five market aspects, so session context cannot inflate it.

---

## 7. Validation Strategy

| Condition | Behaviour |
|---|---|
| Null context, event or configuration | `IntelligenceValidationException` / `NullPointerException` with a fixed message |
| Empty candle series | rejected: at least one candle is required |
| Mixed instruments or intervals in one context | rejected with the field named |
| Non-ascending or duplicate candle timestamps | rejected in the context and in the history |
| Empty indicator map | rejected: missing indicators are never assumed |
| Duplicate indicator types differing only in case | rejected |
| Series name contradicting its key | rejected |
| Empty indicator series | rejected |
| Indicator result timestamp not aligned to the latest candle | rejected |
| Required indicator absent from the context | rejected by the engine before any analyzer runs |
| Out-of-order event in the history | rejected deterministically |
| Indicator update with mismatched candle timestamp | rejected by the history |
| Invalid configuration value | rejected at construction |

Every rejection message names the field or indicator and contains no payload data.

---

## 8. Event Integration

Consumed: `IndicatorUpdatedEvent` only. Every other pipeline event is ignored, so adding intelligence cannot change the meaning of another event.

Published: `MarketAssessmentGeneratedEvent(assessment, occurredAt)`.

`MarketEvent` is a sealed interface, and a sealed interface in the unnamed module requires its permitted subtypes in the same package. The assessment event therefore lives in `com.tradingassistant.pipeline.event` and carries only the immutable `MarketAssessment`. This is an additive extension of the Phase 5 contract: one new permitted record and one new `MarketEventType` constant (`MARKET_ASSESSMENT_GENERATED`); no existing event, field or meaning changed, and the event package gains no behavioural dependency on the intelligence layer.

The service's state is a bounded `ConcurrentHashMap` of `MarketIntelligenceHistory`, one per `AnalysisKey(instrument, interval)`. Histories are independent, so pipeline feed threads do not contend except on their own instrument. The configuration bean is subscribed in `IntelligenceConfiguration`, and the `Subscription` bean is closed by Spring on shutdown.

```text
IndicatorUpdatedEvent
   → MarketIntelligenceHistory.append(...)   (immutable AnalysisContext snapshot)
   → MarketIntelligenceEngine.assess(...)
   → MarketAssessmentGeneratedEvent
```

---

## 9. Performance Notes

- The engine is stateless and shared; analysis allocates only the assessment objects and a small map of latest results.
- History keeps at most `historyWindow` candles and `historyWindow` observations per indicator, so memory is bounded per instrument and independent of session length.
- Windowing is `ArrayDeque` based; `IndicatorSeries` is materialized once per assessment rather than on every append.
- Appearance of the pipeline contract is unchanged: intelligence runs synchronously on the publishing thread, so a slow consumer still delays the feed. Consumers that need asynchrony belong behind their own executor in a later phase.
- A 2 000-event, four-instrument replay with real SMA and ATR calculations completes well inside its 20-second bound; the full 295-test suite runs in roughly 11 seconds on the development machine.
- No latency target is asserted: no approved performance budget exists for this layer, and inventing one would be a fabricated requirement.

---

## 10. Testing Summary

| Test class | Tests | Coverage |
|---|---|---|
| `TrendAnalyzerTest` | 11 | bullish, bearish, conflict, weak ADX, required-but-missing ADX, single vote, ADX-only, warm-up, strict policy, optional ADX, configurable agreement |
| `MomentumAnalyzerTest` | 8 | strengthening, weakening, neutral, configurable bands, partial inputs, strict policy, empty input set, incomplete Stochastic RSI |
| `VolatilityAnalyzerTest` | 8 | expanding, contracting, stable, configurable sensitivity, partial inputs, single observation, zero previous width, strict policy |
| `ParticipationAnalyzerTest` | 6 | strong, weak, average, partial inputs, OBV baseline requirement, configurable CMF threshold |
| `StructureAnalyzerTest` | 7 | higher highs/lows with breakout, lower highs/lows with breakdown, range, configurable tolerance, insufficient candles, partial window, strict policy |
| `SessionContextAnalyzerTest` | 8 | opening, regular, closing, pre-open, after market, weekend, half-day special session, configurable windows |
| `MarketIntelligenceEngineTest` | 7 | full composition, partial assessment, insufficient data, required indicator rejection, null rejection, determinism/immutability, replacement analyzers |
| `MarketIntelligenceServiceTest` | 10 | publication, ignored events, null event, history bounding, out-of-order/duplicate rejection, alignment rejection, instrument isolation, replay parity, 2 000-event replay, history removal |
| `AnalysisContextTest` | 9 | latest accessors, key canonicalization, null/empty input, instrument/interval mismatch, ordering, duplicate types, name mismatch, timestamp mismatch, blank/null/empty series |
| `MarketIntelligenceConfigTest` | 6 | defaults, builder overrides, required-type canonicalization, threshold validation, window/confidence validation, warm-up semantics |
| `IntelligenceConfigurationTest` | 3 | disabled wiring, enabled wiring with one subscription, unsubscription on shutdown |

All 83 tests pass; the complete suite is **295 tests, 0 failures, 0 errors, 2 skipped**.

---

## 11. Problems Encountered

| Problem | Resolution |
|---|---|
| A sealed interface in the unnamed module cannot permit a subtype in another package | Moved `MarketAssessmentGeneratedEvent` into the shared event package with the immutable assessment as its only payload; documented it as additive and kept the event package free of behaviour |
| Counting ADX in neither expected nor usable inputs made a missing configured input look `READY` | ADX is now an expected input when `requireAdxForDirection` is true, so a missing ADX reports `PARTIAL` and `UNKNOWN` instead of a false-ready state |
| Counting the session aspect toward confidence made a zero-evidence assessment look better than it was and made overall `INSUFFICIENT_DATA` unreachable | Confidence and overall sufficiency now consider the five market aspects only; session context is reported alongside them |
| A single neutral input (close equal to EMA) initially returned `UNKNOWN` rather than a description | Directional-input counting was separated from vote counting, so usable-but-neutral evidence reports `SIDEWAYS` |
| A rising-highs/falling-lows window was expected to be an uptrend in a test | It is genuinely mixed: `HIGHER_HIGH` with `LOWER_LOW` reports `UNKNOWN` bias. The test now asserts the mixed signals rather than a wrong bias |
| Volatility expansion cannot be judged from a single observation | The context carries full series, and the analyzer compares with the most recent earlier usable observation |
| Structure initially looked like it needed subjective swing detection | Replaced pivot labelling with an older/newer half comparison, which is deterministic and testable |

---

## 12. Conditions and Limitations

- Thresholds are conventional starting values, not values calibrated against this instrument universe. They are configuration precisely so calibration is a data exercise rather than a code change.
- `Confidence` is a readiness count, not a statistical confidence and not a probability.
- Structure compares window halves; it does not label individual pivots. That choice is documented and deterministic, and can be refined when a later phase needs pivot-level structure.
- One assessment describes one interval. Multi-timeframe confluence is not implemented.
- Histories are in-memory and per instrument; there is no expiry policy until a phase owns operational scheduling.
- No persistence: an assessment is published and forgotten unless a consumer stores it.
- Intelligence runs synchronously on the publishing thread.

---

## 13. Recommendations for Phase 7 (Options Analytics)

Do not implement these here:

1. Build an option-chain domain service before any Greek: chain assembly is a market-data concern, not an analytics one.
2. Keep option analytics a separate engine with its own immutable assessment type; do not extend `MarketAssessment`, so intelligence stays broker-independent and option-free.
3. Reuse the Phase 6 pattern: a validated immutable context, independent analyzers, composition in one engine, and an additive event in the shared event package.
4. Define warm-up and data-quality policy for implied volatility explicitly; an IV series has a far shorter and noisier history than a price series.
5. Decide whether option analytics consumes `IndicatorUpdatedEvent` or a candle event; the intelligence layer should remain unaware of options either way.
6. Keep every option output descriptive: no strategy, no conviction, no sizing.
7. Calibrate intelligence thresholds with real data in a later phase, and version any change so historical assessments remain explainable.
8. Treat persistence and scheduling as their own approved phase rather than embedding them in an analytics engine.

---

## 14. Completion Gate

Phase 6 is implemented, tested and documented. The broker adapter, market domain, indicator engine, pipeline and calendar were not modified beyond the additive event kind required for publication. No Options Analytics, Greeks, implied volatility, PCR, Max Pain, risk, strategy, trade qualification, position sizing, order handling, persistence, database, scheduler, AI, ML or notification functionality was started, and nothing was pushed or deployed.

**STOP — awaiting architectural review. Options Analytics has not been started.**
