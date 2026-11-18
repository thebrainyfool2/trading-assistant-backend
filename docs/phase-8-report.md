# Phase 8 — Strategy Evaluation Engine

**Status: implemented, tested and documented. Awaiting architectural review.**
No recommendation, ranking, trade qualification, risk management, position sizing, capital allocation, order placement, broker communication, portfolio, backtesting, AI/ML, persistence, scheduler or notification functionality was started.

---

## 1. Executive Summary

Phases 2–7 produced descriptions: a broker-independent feed, sixteen technical indicators, a runtime pipeline, a market assessment and an option-chain assessment. None of them answered whether a predefined strategy's conditions are currently true.

Phase 8 adds that answer and nothing more. It evaluates immutable strategy definitions, each composed of independent rules, against one immutable context and publishes immutable evaluations:

```text
MarketAssessmentGeneratedEvent ─┐
                                ├──► StrategyEvaluationService
OptionsAssessmentGeneratedEvent ─┘        │  (candle + indicators + aligned assessments)
                                          ▼
                              StrategyEvaluationEngine
                                          │
                        ┌─────────────────┼─────────────────┐
                        ▼                 ▼                 ▼
                   Strategy A        Strategy B        Strategy N
                        │                 │                 │
                        └─────────────────┼─────────────────┘
                                          ▼
                              List<StrategyEvaluation>
                                          │
                                          ▼
                              StrategyEvaluatedEvent
```

Each evaluation states, at most, which rules passed, which failed, and what could not be decided. There is no recommendation field, score, ranking, probability, expectancy, position, quantity, confidence percentage, entry, stop or target anywhere in the model.

Verification: `mvn -B -ntp verify` → **498 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **110 tests are new in Phase 8**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3.

---

## 2. Architecture

`com.tradingassistant.strategy` (24 main classes, 11 test classes):

| Package | Contents |
|---|---|
| `strategy.api` | `StrategyRule` contract: `id()` and `evaluate(context)` |
| `strategy.domain` | `StrategyDefinition`, `StrategyEvaluation`, `RuleEvaluation`, `StrategyEvaluationContext`, `StrategyCategory`, `EvaluationStatus`, `StrategyInput` |
| `strategy.condition` | `ConditionSupport` read-only lookups shared by rules |
| `strategy.rule` | `IndicatorRules`, `AssessmentRules`, `OptionsRules` — 14 immutable rule records |
| `strategy.engine` | `StrategyEvaluationEngine` |
| `strategy.registry` | `StrategyRegistry`, `BuiltInStrategies` |
| `strategy.service` | `StrategyEvaluationService`, `StrategyMarketDataProvider`, `InMemoryStrategyMarketDataProvider` |
| `strategy.config` | `StrategyEngineConfig` |
| `strategy.validation` | `StrategyValidator` |
| `strategy.exception` | `StrategyException`, `StrategyValidationException`, `StrategyEvaluationException` |
| `strategy` | `StrategyConfiguration` Spring wiring |

Dependency direction:

- rules depend on the context, the condition helpers and standard indicator value keys only;
- the engine depends on the rule contract and domain model, never on a concrete rule;
- the registry depends on definitions and the engine configuration;
- the service depends on the Phase 6/7 assessment events, a provider interface and the event publisher;
- nothing in the strategy layer reads broker adapters, HTTP objects, persistence, the pipeline internals or another strategy.

The engine is stateless. Definitions and rules are immutable. Strategies never communicate with each other: each is evaluated independently, and no evaluation can influence another.

---

## 3. Strategy Model

**`StrategyDefinition`** — `id`, `name`, `description`, `enabled`, `category`, `requiredInputs`, ordered `rules`. Validated at construction: blank id or name, an empty rule list, null rules, a blank rule id and a duplicate rule id are all rejected. `withEnabled(boolean)` returns a new definition; the original is untouched.

**`StrategyCategory`** — `TREND`, `MOMENTUM`, `BREAKOUT`, `REVERSAL`, `VOLATILITY`, `OPTIONS`, `INTRADAY`, `SWING`, `CUSTOM`. Descriptive only; the category never changes evaluation.

**`StrategyInput`** — `MARKET_ASSESSMENT`, `OPTIONS_ASSESSMENT`, `CANDLE`, `INDICATORS`. A definition declares what it needs so strict evaluation can report a missing input once instead of letting every rule rediscover it.

**`StrategyEvaluation`** — `strategyId`, `strategyName`, `timestamp`, `instrument`, `interval`, `status`, `ruleEvaluations`, `summary`. Immutable, with copied rule list. `passed()` is derived from the status, so an evaluation can never claim both.

**Status semantics**

| Status | Meaning |
|---|---|
| `PASSED` | every rule passed |
| `FAILED` | at least one rule failed (a failed condition is a definite answer) |
| `PARTIAL` | some rules passed while others had no usable data |
| `INSUFFICIENT_DATA` | no rule could be decided because inputs were missing |
| `UNKNOWN` | rules produced no decision, for example after a contained rule failure |

Precedence: any `FAILED` wins; then all-passed; then all-insufficient; then partial when at least one passed; otherwise `UNKNOWN`.

**`StrategyEvaluationContext`** — `MarketAssessment`, optional `OptionsAssessment`, optional `Candle`, indicator map keyed by canonical type. Session context is derived from the market assessment rather than stored twice (`context.session()`).

---

## 4. Rule System

`StrategyRule` exposes a stable `id()` and one `RuleEvaluation evaluate(context)`. A rule receives the immutable context and its own configuration only: no registry, no engine, no other rule, no cached state. That is what makes rules independently evaluable, order-independent and safe to run in parallel.

`RuleEvaluation` carries the rule id, a status, a deterministic reason and descriptive metadata (observed values and the threshold compared). `passed()` is derived from the status, so the boolean can never disagree with the outcome. The four factories are `passed`, `failed`, `insufficient` and `unknown`.

Fourteen immutable rule records cover the sample strategies:

| Rule | Condition |
|---|---|
| `IndicatorRules.CloseAboveIndicator` | close above the named indicator (EMA, VWAP) |
| `IndicatorRules.IndicatorAtLeast` | indicator value ≥ threshold (ADX strength) |
| `IndicatorRules.IndicatorAtMost` | indicator value ≤ threshold (RSI pullback) |
| `IndicatorRules.SupertrendBullish` | Supertrend direction is positive |
| `IndicatorRules.MacdHistogramPositive` | MACD histogram is positive |
| `AssessmentRules.StructureSignalPresent` | market structure reports a named signal |
| `AssessmentRules.MarketVolatilityState` | market volatility state matches the configured state |
| `AssessmentRules.VolumeAtLeast` | candle volume ≥ minimum |
| `OptionsRules.IvExpansion` | option implied volatility is expanding |
| `OptionsRules.PcrAtLeast` | chain PCR by open interest ≥ threshold |
| `OptionsRules.MaxPainWithinPercent` | max-pain strike within a distance of spot |
| `OptionsRules.ExpectedMoveAtLeastPercent` | expected expiry move ≥ a share of spot |
| `OptionsRules.LiquidityAtLeast` | chain liquidity is liquid |

Every rule reports `INSUFFICIENT_DATA` with a field-specific reason when its input is absent (missing indicator, missing candle, no options assessment, unavailable section), and never substitutes a default.

**Built-in sample strategies** (all deterministic rule collections, all enabled by default):

| Id | Category | Rules |
|---|---|---|
| `EMA_TREND` | TREND | close above EMA |
| `EMA_ADX` | TREND | close above EMA, ADX ≥ 20 |
| `SUPERTREND` | TREND | Supertrend direction positive |
| `VWAP_TREND` | INTRADAY | close above VWAP |
| `RSI_PULLBACK` | REVERSAL | RSI ≤ 45 |
| `MACD_MOMENTUM` | MOMENTUM | MACD histogram positive |
| `BOLLINGER_EXPANSION` | VOLATILITY | market volatility expanding |
| `BREAKOUT` | BREAKOUT | structure reports breakout candidate |
| `BREAKDOWN` | BREAKOUT | structure reports breakdown candidate |
| `HIGH_VOLUME` | INTRADAY | candle volume ≥ 100 000 |
| `OPTION_IV_EXPANSION` | OPTIONS | option IV expanding |
| `PCR_EXTREME` | OPTIONS | chain PCR by OI ≥ 1.5 |
| `MAX_PAIN_PROXIMITY` | OPTIONS | max pain within 2% of spot |
| `EXPECTED_MOVE` | OPTIONS | expected move ≥ 1% of spot |
| `LIQUIDITY_FILTER` | OPTIONS | chain liquidity liquid |

Thresholds are fixed defaults so an evaluation is reproducible; a caller wanting different thresholds constructs its own definition from the same rule records.

---

## 5. Registry

`StrategyRegistry` is a thread-safe map of immutable definitions with atomic per-definition operations:

- `register` — rejects a duplicate identifier and enforces `maxStrategies` and `maxRules`;
- `replace` — atomically swaps a definition with the same id and rejects an unknown id;
- `unregister`, `lookup`, `contains`;
- `enabled()`, `disabled()`, `snapshot()` — immutable lists sorted by identifier, so an evaluation always sees one consistent, ordered set;
- `size()`.

No definition is mutated in place: enabling or disabling means registering a replacement. Duplicate detection uses `putIfAbsent`, so two concurrent registrations of the same id cannot both succeed.

`BuiltInStrategies` is the deterministic catalog of the fifteen sample definitions.

---

## 6. Evaluation Engine

`StrategyEvaluationEngine` receives the context and a list of definitions, validates the batch, filters disabled strategies, and evaluates every rule of every enabled strategy.

- **Input sufficiency** — with `strictValidation` (the default), a definition whose declared input is absent is reported as `INSUFFICIENT_DATA` without evaluating rules, and every rule carries a reason naming the missing input. With `strictValidation=false`, rules run and report their own insufficiency, which lets a caller inspect partial data.
- **Rule isolation** — each rule is evaluated independently against the same context; a rule's outcome cannot change another rule's input or result.
- **Failure containment** — an unexpected rule exception becomes an `UNKNOWN` rule when `failFast=false`, and a `StrategyEvaluationException` when `failFast=true`.
- **Parallel evaluation** — when `parallelEvaluation=true` and more than one strategy is enabled, each strategy is submitted to the injected `Executor`; results are reassembled in input order, so the output is identical to sequential evaluation.
- **Limits** — the batch size is checked against `maxStrategies`, each definition against `maxRules`, and duplicate ids inside one batch are rejected.
- **Determinism** — the engine holds no state and adds no time source; identical inputs produce equal evaluations.

The engine answers exactly one question per strategy: are its predefined conditions currently true?

---

## 7. Event Integration

Consumed:

- `MarketAssessmentGeneratedEvent` — evaluated immediately, using the cached options assessment of the same instant when one exists;
- `OptionsAssessmentGeneratedEvent` — evaluated only when a cached market assessment of the same instrument and instant exists.

Published: `StrategyEvaluatedEvent(List<StrategyEvaluation>, occurredAt)`.

`MarketEvent` is sealed, and a sealed interface in the unnamed module requires its permitted subtypes in the same package, so the event lives in `com.tradingassistant.pipeline.event` and carries only immutable evaluations. This is an additive extension: one permitted record and one `MarketEventType` constant (`STRATEGY_EVALUATED`). No existing event, field or meaning changed.

Service behaviour:

| Situation | Behaviour |
|---|---|
| market event, no options cached | strategies are evaluated with market inputs only; option strategies report `INSUFFICIENT_DATA` |
| market event, options of the same instant cached | evaluated with both assessments |
| options event, aligned market cached | re-evaluated with both assessments |
| options event, no aligned market | ignored; nothing is published |
| engine disabled | every event is ignored and no cache is written |
| registry has no enabled strategies | nothing is published |

Caches are bounded per instrument (64 entries, access-ordered) and cleared on demand. They are staging, not storage.

---

## 8. Validation

| Condition | Behaviour |
|---|---|
| Null event | `StrategyValidationException("Event must not be null.")` |
| Null definition, context, config or executor | rejected deterministically |
| Blank strategy id | rejected, naming `id` |
| Blank strategy name | rejected, naming `name` |
| Empty or null rule list | rejected, naming `rules` |
| Blank rule id | rejected, naming `rule id` |
| Duplicate rule id inside a definition | rejected, naming the id |
| Duplicate strategy id on registration | rejected, naming the id |
| Replacement of an unknown id | rejected, naming the id |
| More strategies than `maxStrategies` | rejected by the registry, the batch validator and the engine |
| More rules than `maxRules` | rejected by the registry and definition validator |
| Null market assessment | `NullPointerException` before evaluation |
| Options assessment of another instrument | rejected, naming `optionsAssessment` |
| Options assessment at another timestamp | rejected, naming the timestamp |
| Candle of another instrument, interval or timestamp | rejected, naming the field |
| Indicator at another timestamp | rejected, naming the indicator |
| Indicator key disagreeing with its result type | rejected, naming the key |
| Duplicate or blank indicator keys | rejected |
| Negative metric thresholds (volume, PCR, distance) | rejected at rule construction |
| Batch containing a null entry | rejected |

Every message names the offending field or identifier and contains no payload data.

---

## 9. Configuration

`StrategyEngineConfig` is an immutable record with a builder and construction-time validation:

| Knob | Default | Purpose |
|---|---|---|
| `enabled` | true | whether the service evaluates at all |
| `parallelEvaluation` | false | evaluate strategies through the injected executor |
| `maxStrategies` | 128 | maximum strategies per evaluation (1–10 000) |
| `maxRules` | 32 | maximum rules per definition (1–1 000) |
| `failFast` | false | propagate a rule failure instead of containing it |
| `strictValidation` | true | treat a missing declared input as insufficient data |

There is no runtime mutation; changing behaviour means constructing a new configuration. Rule thresholds are part of each definition, not of engine configuration, so the engine's behaviour is independent of any particular strategy.

---

## 10. Performance

- The engine allocates only the evaluation objects; there is no cache, no mutable state and no shared counter.
- Rules are immutable singletons inside definitions and are evaluated once per strategy; nothing is recomputed across strategies.
- Parallelism is opt-in and uses only the injected executor. The application wires a direct executor, so no thread pool is created or owned by this layer; a caller that supplies a pool gets ordered, deterministic results.
- The registry serves immutable snapshots from a concurrent map, so evaluation never blocks registration beyond one map read.
- Caches in the service are bounded at 64 instruments, access-ordered, so memory cannot grow without limit during a long session.
- Measured in this environment: the complete 498-test suite finishes in about 20 s, of which the 110 strategy tests take roughly 0.8 s. No latency target is asserted, because no approved budget exists for this layer.

---

## 11. Testing

| Test class | Tests | Coverage |
|---|---|---|
| `StrategyEngineConfigTest` | 5 | defaults, overrides, rebuild, invalid limits, null handling |
| `StrategyDefinitionTest` | 8 | identity, blank id/name, empty rules, duplicate and blank rule ids, immutable lists, enabled copy, missing category |
| `StrategyEvaluationContextTest` | 10 | derived session, key canonicalisation, foreign instrument, mismatched options/candle/indicator timestamps, key/type mismatch, duplicate and blank keys, null assessment |
| `StrategyEvaluationEngineTest` | 18 | passed/failed/partial/insufficient/unknown aggregation, disabled filtering, strict and lenient input handling, contained and fail-fast rule failures, parallel order and determinism, duplicate batch ids, limits, null inputs, immutability |
| `StrategyRegistryTest` | 12 | register, duplicate detection, replacement, unknown replacement, unregister, lookup, enabled/disabled ordering, immutable snapshots, strategy and rule limits, seeding, null handling |
| `BuiltInStrategiesTest` | 17 | catalog shape and every sample strategy's pass and fail path, insufficient inputs, full-catalog evaluation |
| `IndicatorRulesTest` | 9 | close-above pass/fail/insufficient, at-least, at-most, Supertrend, MACD, threshold validation |
| `AssessmentRulesTest` | 7 | structure signal present/absent/unavailable, volatility state, volume rule, invalid minimum |
| `OptionsRulesTest` | 10 | IV expansion, PCR, max pain distance, expected move, liquidity, missing options, invalid thresholds |
| `StrategyEvaluationServiceTest` | 11 | publication on market and aligned options events, unaligned options, unrelated events, disabled engine, no enabled strategies, instrument isolation, cache clearing, stale candle/indicator filtering, null event |
| `StrategyConfigurationTest` | 3 | disabled wiring, enabled wiring with built-ins and one subscription, unsubscription on shutdown |

All 110 tests pass; the complete suite is **498 tests, 0 failures, 0 errors, 2 skipped**.

---

## 12. Problems Encountered

| Problem | Resolution |
|---|---|
| A sealed event interface cannot permit a subtype in another package | Added `StrategyEvaluatedEvent` to the shared event package with immutable evaluations as its only payload |
| `java.util.Executor` exposes `execute`, not `submit`, so the first parallel implementation did not compile | Wrapped each evaluation in a `FutureTask` and executed it through the injected `Executor`, preserving the no-pool-ownership rule |
| `Map.containsValue(null)` throws on immutable maps instead of returning false | The context now iterates entries to reject null indicator keys and values |
| The intelligence and options layers both define `VolatilityState`, so rule packages that need both were ambiguous | Rule signatures name the layer explicitly; test fixtures use one simple import and fully qualify the other |
| A `@FunctionalInterface` annotation was invalid because a rule has two abstract methods | Removed the annotation; rules are implemented by immutable records |
| Strict input checking could hide per-rule detail | Missing declared inputs still produce one `INSUFFICIENT_DATA` rule evaluation per rule, naming the input |
| Options and market assessments arrive as separate events | The service caches both and evaluates only when instrument and timestamp align, so no strategy compares values from different moments |
| Duplicate evaluations when both events describe the same instant | Accepted and documented: the market event evaluates the market view, the options event re-evaluates with the chain; neither mutates state |

---

## 13. Known Limitations

- The engine evaluates conditions; it does not backtest, replay history, measure success rates or compare strategies. There is no expectancy, no probability and no ranking, by design.
- Built-in thresholds are fixed defaults, not calibrated values. A caller can define its own thresholds, but no calibration data is produced here.
- The built-in catalog covers the listed examples only; there is no expression language, no rule composition and no parameterised strategy template.
- Strategies cannot reference one another and cannot aggregate across intervals or instruments. That is a deliberate constraint of this phase.
- Indicator values and candles must be supplied through `StrategyMarketDataProvider`; no adapter populates it automatically yet, and values that do not match the assessed timestamp are ignored.
- The service evaluates on the market-assessment timestamp. When option quotes are newer than the spot candle, the option sections describe a slightly earlier instant.
- Each candle can produce two evaluations (one from the market event, one from an aligned options event). No deduplication, debounce or idempotency key is implemented.
- There is no persistence: evaluations exist only in the published event. Nothing survives a restart.
- There is no operational scheduling: evaluation happens when an assessment event arrives.
- Rule metadata is minimal and descriptive; it is not a structured explanation model.

---

## 14. Recommendations for Phase 9

Do not implement these here:

1. Treat `PASSED` as one input to a later qualification step, never as a trade instruction. The strategy layer must stay free of recommendation, ranking and selection.
2. Define trade qualification as its own engine with explicit, configurable criteria and an immutable result model; do not add those fields to `StrategyEvaluation`.
3. Decide explicitly how multiple evaluations of the same candle are handled (latest-wins, dedupe by timestamp, or qualification on demand) before building a consumer.
4. Keep risk, sizing, capital allocation and portfolio state in later, separately reviewed phases; none of them belongs in qualification inputs yet.
5. Add a recorded evaluation replay fixture so future rule changes can be compared deterministically against the same assessments.
6. If evaluation volume grows, consider a consumer-owned executor and a bounded evaluation queue rather than adding internal scheduling to this layer.
7. Calibrate built-in thresholds against recorded assessments once a replay harness exists, and version any change that alters historical evaluations.
8. Keep persistence and scheduling out of the strategy layer; if evaluations must be retained, that is a storage phase with its own contract.

---

## 15. Completion Gate

Phase 8 is implemented, tested and documented. The broker adapter, market domain, indicator engine, pipeline, calendar, market intelligence and options analytics were not modified beyond the additive event kind required for publication. No recommendation, ranking, trade qualification, risk management, position sizing, capital allocation, order placement, broker communication, portfolio, backtesting, AI/ML, persistence, scheduler or notification functionality was started, and nothing was pushed or deployed.

**STOP — awaiting architectural review. Trade Qualification Engine has not been started.**
