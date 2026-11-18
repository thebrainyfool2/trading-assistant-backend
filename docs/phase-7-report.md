# Phase 7 — Option Chain & Options Analytics Engine

**Status: implemented, tested and documented. Awaiting architectural review.**
No strategy, recommendation, signal, entry/exit, risk, sizing, capital allocation, portfolio, order, broker-execution, database, persistence, scheduler, notification, AI, ML or backtesting functionality was started.

---

## 1. Executive Summary

Phases 2–6 produced a broker-independent market feed, a technical-indicator engine, a runtime pipeline and a market-intelligence layer that describes trend, momentum, volatility, participation, structure and session context. None of that described the derivatives market.

Phase 7 adds that description and nothing more. It turns raw option contracts and quotes into an immutable chain, then into one immutable `OptionsAssessment` per capture:

```text
OptionContract[] + Quote[] + spot price
        │
        ▼
OptionChainBuilder            grouping, ordering, validation, moneyness, expiry classification
        │
        ▼
ChainSnapshot                 immutable chain + spot + capture metadata
        │
        ▼
ImpliedVolatilityResolver     per-leg IV from the market premium
        │
        ├── VolatilityAnalyzer        ATM IV, change, rank, percentile, trend, smile, skew, surface
        ├── GreeksAnalyzer            per-leg Greeks and open-interest-weighted listed exposure
        ├── OpenInterestAnalyzer      OI, OI change, volume, concentration, zones, build-up
        ├── PutCallRatioAnalyzer      PCR by OI and volume, chain-wide and per expiry
        ├── MaxPainAnalyzer           pain curve, max-pain strike, nearest strikes
        ├── ExpectedMoveAnalyzer      expiry, daily and weekly expected move
        └── LiquidityAnalyzer         spreads, wide/thin detection, tradability band
        │
        ▼
OptionsAssessment
        │
        ▼
OptionsAssessmentGeneratedEvent
```

The engine describes; it never prescribes. There is no score, no BUY, no SELL, no LONG, no SHORT, no entry, no exit, no target, no stop, no size and no recommendation anywhere in the model or the analytics.

Verification: `mvn -B -ntp verify` → **388 tests, 0 failures, 0 errors, 2 skipped, BUILD SUCCESS**, of which **93 tests are new in Phase 7**. The two skipped tests are the opt-in live broker tests from Phases 2 and 3.

---

## 2. Option Chain Architecture

`com.tradingassistant.options` (60 main classes, 14 test classes):

| Package | Contents |
|---|---|
| `options.domain` | Immutable chain, assessment, Greeks, volatility, interest, ratio, pain, liquidity and context models, plus classification enums |
| `options.chain` | `OptionChainBuilder`, `MoneynessClassifier`, `ExpiryClassifier` |
| `options.greeks` | `BlackScholesModel`, `GreeksEngine` |
| `options.volatility` | `ImpliedVolatilitySolver`, `ImpliedVolatilityResolver`, `ImpliedVolatilityHistory`, `VolatilityAnalyzer` |
| `options.analysis` | `GreeksAnalyzer`, `OpenInterestAnalyzer`, `PutCallRatioAnalyzer`, `MaxPainAnalyzer`, `ExpectedMoveAnalyzer`, `LiquidityAnalyzer` |
| `options.engine` | `OptionsAnalyticsEngine` composition |
| `options.service` | `OptionChainProvider`, `InMemoryOptionChainProvider`, `OptionsAnalyticsService` |
| `options.config` | Immutable `OptionsAnalyticsConfig` |
| `options.math` | `DecimalMath`, `ExpiryTime` |
| `options.exception` | `OptionsException`, `OptionsValidationException` |
| `options` | `OptionsConfiguration` Spring wiring |

Dependency direction:

- the chain builder depends on the Phase 3 market domain only;
- analyzers depend on the domain, the configuration and the pricing/volatility helpers;
- the engine depends on the analyzer contracts and never the reverse;
- the service depends on the Phase 6 event contract and a provider interface;
- nothing in the options layer reads pipeline internals, broker adapters, HTTP objects or persistence.

`OptionsAnalysisContext` is the only way a chain enters analytics, and it ties the capture to the market assessment of the same instrument and instant.

---

## 3. Domain Model

**Chain structure**

| Type | Meaning |
|---|---|
| `Strike` | one quoted leg: `OptionContract`, `Quote`, `Moneyness`, with bid/ask/premium/OI helpers |
| `StrikeGroup` | one strike with optional call and put legs |
| `ExpiryChain` | expiry date, `ExpiryKind`, `ExpiryProximity`, days to expiry, strike groups |
| `OptionChain` | spot instrument, underlying, derivative segment, ordered expiries |
| `ChainSnapshot` | `OptionChain` plus capture instant, spot price, status and diagnostics |

**Classification** — `Moneyness` (`ATM`, `ITM`, `OTM`, `DEEP_ITM`, `DEEP_OTM`), `ExpiryKind` (`WEEKLY`, `MONTHLY`), `ExpiryProximity` (`NEAREST`, `NEXT`, `FAR`). All are enums; a classification is never a free-form string.

**Analytics models**

| Type | Contents |
|---|---|
| `Greeks`, `GreeksAssessment` | delta, gamma, theta, vega, rho with fixed units; model name, status and summary |
| `VolatilitySummary` | ATM IV, IV change, IV rank, IV percentile, skew, trend, state, smile, prepared surface |
| `SmilePoint`, `IvSurfacePoint` | strike/type IV observations, surface cell by expiry, strike and type |
| `OpenInterestSummary`, `StrikeOpenInterest` | totals, per-strike OI/volume and changes, concentration, support/resistance zones, build-up |
| `PutCallRatioSummary`, `ExpiryPutCallRatio` | chain and per-expiry ratios by OI and volume |
| `MaxPainSummary`, `PainPoint` | max-pain strike, full pain curve, nearest strikes |
| `ExpectedMoveSummary` | expiry, daily and weekly moves, upper and lower bounds, method |
| `LiquiditySummary` | assessed legs, wide legs, thin legs, average spread, state, tradability |
| `ChainSummary` | expiry count, strike count, leg count, nearest expiry, spot, status |
| `OptionsAssessment` | every summary plus session context, status, warm-up flag and diagnostics |

`AssessmentStatus` and `SessionAssessment` are reused from the intelligence layer because the vocabulary is identical and the two layers already meet at `MarketAssessment`. No intelligence behaviour is extended or changed.

---

## 4. Chain Builder Design

`OptionChainBuilder.build(spotInstrument, underlying, spotPrice, capturedAt, contracts, quotes)`:

1. **Validate the capture** — non-null inputs, positive spot, at least one contract, no null contracts, non-blank underlying.
2. **Enforce one underlying and one derivative segment** — any foreign underlying or segment rejects the whole capture rather than silently dropping contracts.
3. **Reject duplicates** — by instrument key, and by `(expiry, canonical strike, option type)`, so a re-listed token cannot create two legs at the same strike.
4. **Exclude expired expiries** — an expiry before the capture date is dropped and counted, because a stale contract in a feed must not distort moneyness or expected move.
5. **Exclude unquoted contracts** — a leg without a usable quote (no last price and no depth) is dropped and counted. A capture that leaves no quoted leg is rejected: analytics never rest on missing data.
6. **Order deterministically** — expiries ascending, strikes ascending with `BigDecimal.compareTo`, so `500`, `500.0` and `500.00` are one strike.
7. **Classify expiries** — the last expiry of a calendar month is monthly, every other expiry of that month is weekly; proximity is ordinal (nearest, next, far).
8. **Classify moneyness per expiry** — all strikes inside the configured ATM band are ATM; when the band is empty the nearest strike becomes ATM; remaining legs are ITM/OTM by type and become deep beyond the configured distance.
9. **Build the snapshot** — status `READY` when nothing was excluded, `PARTIAL` otherwise, with deterministic diagnostics: contract count, quoted legs, unquoted contracts, expired contracts, expiry count, strike count and ATM band.

Classification is split into `MoneynessClassifier` and `ExpiryClassifier` so each rule is testable without building a chain, and the builder stays a composition of small decisions.

---

## 5. Greeks Engine

`BlackScholesModel` prices European options with a continuous dividend yield; `GreeksEngine` adds configuration, time resolution and validation.

```text
d1 = (ln(S/K) + (r − q + σ²/2)T) / (σ√T)
d2 = d1 − σ√T
call = S e^{−qT} Φ(d1) − K e^{−rT} Φ(d2)
put  = K e^{−rT} Φ(−d2) − S e^{−qT} Φ(−d1)
```

Units are fixed in the model: delta per one unit of underlying, gamma per unit, theta per calendar day, vega per one volatility point, rho per one rate point. The day count is configuration, so theta is comparable across environments.

Arithmetic is `BigDecimal` throughout. `DecimalMath` supplies `exp`, `ln`, `sqrt`, the normal density and the normal CDF in DECIMAL128 context, with the CDF using the Abramowitz–Stegun 26.2.17 rational approximation (absolute accuracy about 1.5e-7). That bound is the documented precision boundary for Greeks and probabilities; no platform `double` maths is used in the pricing path.

Edge handling is explicit:

- zero time to expiry returns an `UNKNOWN` assessment instead of a fabricated Greek;
- zero volatility yields discounted forward intrinsic value and a step delta;
- extreme moneyness short-circuits the normal density to zero instead of overflowing the exponential;
- volatility outside the configured range is rejected;
- a model result outside delta `[0,1]` for calls or `[-1,0]` for puts, or with negative gamma or vega, is rejected as invalid.

`GreeksAssessment` never exposes internal terms: only the five sensitivities, the model name and a summary.

---

## 6. IV Engine

`ImpliedVolatilitySolver` inverts the model with a bracketed bisection between the configured minimum and maximum volatility:

- if the market premium is outside the attainable premium range, the result is empty rather than a boundary value;
- iteration stops on premium tolerance or a minimum bracket width;
- zero premium, negative premium and expiry-day captures return empty;
- the search is deterministic for a given configuration.

`ImpliedVolatilityResolver` solves every leg that carries a premium and returns an immutable `Map<OptionLegKey, BigDecimal>`; unresolved legs are omitted rather than assigned a placeholder.

`ImpliedVolatilityHistory` is a bounded in-memory ring per underlying:

- observations must be non-decreasing in time; the same instant replaces the last value, so replaying one candle is idempotent;
- `change` is current minus previous;
- `rank` is the current value's position inside the observed range, in percent, and is empty while the range is flat or shorter than two observations;
- `percentile` is the share of observations strictly below the current value;
- the window is bounded by configuration and nothing is persisted.

Derived descriptions:

| Output | Rule |
|---|---|
| ATM IV | mean of the solved ATM call and put volatilities, whichever exist |
| IV change | current ATM IV minus the previous observation |
| IV rank / percentile | from the history window, empty until it is usable |
| IV trend | `RISING`, `FALLING`, `FLAT` against the configured threshold, `UNKNOWN` without a change |
| IV expansion / contraction | `EXPANDING`, `CONTRACTING`, `STABLE`, `UNKNOWN`, derived from the trend |
| IV smile | IV by strike and type inside the configured window around ATM |
| IV skew | mean OTM put IV minus mean OTM call IV in the same window |
| IV surface | every solved leg as `(expiry, strike, type, IV)`, prepared and immutable |

The statistics are computed **before** the current ATM observation is recorded, so rank and percentile describe the history that preceded the capture rather than including themselves.

---

## 7. Open Interest Analytics

`OpenInterestAnalyzer` describes, per expiry and strike: call/put open interest, call/put volume, their changes against the previous capture, and the positioning implied by premium change together with open-interest change.

| Premium change | OI change | Description |
|---|---|---|
| up | up | `LONG_BUILD_UP` |
| down | up | `SHORT_BUILD_UP` |
| down | down | `LONG_UNWINDING` |
| up | down | `SHORT_COVERING` |
| flat or no change | any | `NEUTRAL` |
| unavailable | unavailable | `UNKNOWN` |

Concentration is the strike with the largest call or put open interest. Support zones are the strikes with the largest put open interest; resistance zones are the strikes with the largest call open interest, limited by configuration. The summary is explicit that these describe where open interest sits; they are not levels, targets or signals.

Changes are empty until a previous capture exists, which is why the first capture reports `PARTIAL` rather than inventing movement.

---

## 8. PCR & Max Pain

**Put/call ratios** are computed by open interest and by volume, for the chain and per expiry, according to `PcrAggregation` (`CHAIN`, `EXPIRY`, `BOTH`). A ratio is produced only when the call denominator is positive; a ratio against zero would not be a description.

**Max pain** is computed for the nearest expiry. For every candidate settlement strike:

```text
callPain(K) = Σ over strikes s < K of (K − s) × callOI(s)
putPain(K)  = Σ over strikes s > K of (s − K) × putOI(s)
totalPain(K)= callPain(K) + putPain(K)
```

The max-pain strike has the smallest total; ties resolve to the lower strike. The output carries the full curve and the nearest strikes by distance, all immutable. Open interest is used directly as the contract count, so pain is expressed in premium points per lot. The result describes where the smallest aggregate payout would occur; it is not a target.

---

## 9. Liquidity Analytics

`LiquidityAnalyzer` inspects every leg with a two-sided quote:

- spread = ask − bid, relative to the mid, in percent;
- **wide spread** when the relative spread exceeds the configured maximum;
- **thin market** when open interest or volume is below the configured floor;
- **aggregate state**: illiquid when more than half of the assessed legs are wide or thin, moderate when some are, liquid when none are;
- **tradability**: `HIGH` for liquid, `MODERATE` for moderate, `LOW` for illiquid, `UNKNOWN` when no two-sided quote exists;
- **average spread** across assessed legs.

Legs without a two-sided quote are not assessed at all, so the summary states how many legs the conclusion rests on. Tradability describes how easily the quoted legs could be transacted; it says nothing about whether they should be.

---

## 10. Validation Strategy

| Condition | Behaviour |
|---|---|
| Null event | `OptionsValidationException("Event must not be null.")` |
| Null context, chain, assessment or configuration | rejected deterministically |
| Empty contract list | rejected: at least one contract is required |
| Missing or non-positive spot price | rejected |
| Mixed underlyings | rejected: exactly one underlying per chain |
| Mixed derivative segments | rejected: exactly one segment per chain |
| Duplicate instrument key | rejected: duplicate option contract |
| Duplicate `(expiry, strike, type)` | rejected, including different BigDecimal scales |
| Expired expiry | excluded from the chain and counted in diagnostics |
| Missing quote for a leg | leg excluded and counted; capture rejected when nothing remains |
| Chain and assessment of different instruments | rejected |
| Chain capture and assessment timestamps differ | rejected |
| Expiry before capture | Greeks return `UNKNOWN`, IV returns empty |
| Volatility at or below zero, or outside the configured range | rejected |
| Premium that no volatility can reproduce | IV empty, never a fabricated value |
| Model Greeks outside plausible bounds | rejected as invalid Greeks |
| Out-of-order volatility observation | rejected |
| Invalid configuration value | rejected at construction |
| Empty or zero-denominator ratio | ratio reported as unavailable |

Every message names the field or condition and contains no payload data.

---

## 11. Event Integration

Consumed: `MarketAssessmentGeneratedEvent` only. Every other event is ignored, so adding options analytics cannot change the meaning of an existing event.

Published: `OptionsAssessmentGeneratedEvent(assessment, occurredAt)`.

`MarketEvent` is sealed, and a sealed interface in the unnamed module requires its permitted subtypes in the same package, so the new event lives in `com.tradingassistant.pipeline.event` and carries only the immutable `OptionsAssessment`. This is an additive extension — one permitted record and one `MarketEventType` constant (`OPTIONS_ASSESSMENT_GENERATED`); no existing event, field or meaning changed.

Flow inside the service:

```text
MarketAssessmentGeneratedEvent
   → OptionChainProvider.snapshotFor(instrument, timestamp)   (empty → ignore)
   → OptionsAnalysisContext(chain, assessment, previousChain)
   → OptionsAnalyticsEngine.analyze(context, volatilityHistory)
   → OptionsAssessmentGeneratedEvent
   → ChainStateSnapshot.of(chain) stored as the new previous state
```

State is per underlying: a volatility history ring and the previous chain state. Each underlying is guarded by its own lock, so instruments never contend and none can contaminate another. Publication happens outside the lock. Instruments with no registered chain are ignored, which lets the service run beside intelligence without knowing which instruments list options.

---

## 12. Performance Notes

- The chain builder is linear in the number of contracts and quotes, using ordered maps and sets so no sort of the whole universe is needed.
- Analytics iterate each leg once per component; the resolver solves IV once per priced leg and the result is shared with the volatility, Greeks and expected-move analyzers instead of being recomputed.
- Memory is bounded per capture by the chain itself; history is bounded by `ivHistoryWindow` per underlying; nothing accumulates across captures except that window and one previous state map.
- The engine is stateless and holds only immutable configuration plus stateless analyzers, so it is safe to call concurrently. Per-underlying work is independent, which allows a caller to process instruments in parallel; the service deliberately serializes only within one underlying, because the history and previous state belong to it.
- Measured in this environment: a 410-leg, five-expiry chain is built and fully assessed in roughly 0.3 s inside the test run, and the complete 388-test suite finishes in about 11.6 s. No latency target is asserted, because no approved budget exists for this layer.
- No global mutable state, no caches, no thread pools and no background daemons were introduced.

---

## 13. Testing Summary

| Test class | Tests | Coverage |
|---|---|---|
| `OptionChainBuilderTest` | 16 | grouping, ordering, strike groups, ATM band and fallback, ITM/OTM/deep, weekly/monthly, proximity, duplicate contract, duplicate strike, mixed underlying, mixed segment, empty input, bad spot, expired exclusion, unquoted exclusion, no-quote rejection, immutability, configured ATM band |
| `GreeksEngineTest` | 8 | ATM sensitivities, call/put delta parity, deep ITM/OTM delta, volatility effect, expiry handling, invalid volatility, determinism |
| `ImpliedVolatilityTest` | 10 | call and put IV recovery, unattainable premium, expiry day, change/rank/percentile, insufficient history, window bound, same-instant replacement, backwards rejection, non-positive rejection |
| `VolatilityAnalyzerTest` | 7 | ATM IV, surface, history-derived rank and trend, skew, insufficient data, partial data, multi-expiry surface, resolver coverage |
| `OpenInterestAnalyzerTest` | 6 | totals, concentration, zones, change against previous, four build-up cases, unknown without history, insufficient data, configured zone limit |
| `PutCallRatioAnalyzerTest` | 5 | chain ratios, per-expiry ratios, chain-only aggregation, expiry-only aggregation, zero denominator |
| `MaxPainAnalyzerTest` | 5 | symmetric max pain, minimal curve, intrinsic pain formula, nearest-strike limit, insufficient data |
| `ExpectedMoveAnalyzerTest` | 3 | volatility method with bounds, straddle method, unavailable without IV |
| `LiquidityAnalyzerTest` | 5 | liquid chain, wide spreads, thin market, configured threshold, unknown without depth |
| `OptionsAnalyticsEngineTest` | 7 | first-capture partial, ready after history, session context, diagnostics and immutability, timestamp mismatch, instrument mismatch, determinism |
| `OptionsAnalyticsServiceTest` | 8 | publication, unregistered instrument, unrelated events, history retention, underlying isolation, replay idempotence, state clearing, null event |
| `OptionsAnalyticsConfigTest` | 8 | defaults, overrides, rebuild, year fractions, invalid percentages, invalid volatility bounds, invalid windows, missing enumerations |
| `OptionsScaleTest` | 2 | 410-leg five-expiry chain within a time bound; assessment immutability |
| `OptionsConfigurationTest` | 3 | disabled wiring, enabled wiring with one subscription, unsubscription on shutdown |

All 93 tests pass; the complete suite is **388 tests, 0 failures, 0 errors, 2 skipped**.

---

## 14. Problems Encountered

| Problem | Resolution |
|---|---|
| A sealed event interface cannot permit a subtype in another package | Added `OptionsAssessmentGeneratedEvent` to the shared event package with the immutable assessment as its only payload; documented as additive |
| Greeks validation rejected the signed, scaled net exposure | Bounds belong to one leg, not to an aggregate: per-leg plausibility moved into `GreeksEngine`, and `Greeks` became a plain immutable value holder |
| Deep in/out-of-the-money legs produced `d1` near ±55 and overflowed the exponential | The normal density short-circuits to zero beyond the representable tail, so extreme strikes return clean 0/1 probabilities |
| Rank and percentile were initially computed after recording the current value | Statistics are computed from the prior window and only then is the current observation appended, matching how rank is defined |
| A quote of zero premium silently produced "insufficient data" rather than a misleading IV | The resolver omits unsolvable legs; the analyzer reports `INSUFFICIENT_DATA`/`PARTIAL` explicitly |
| Cheap wing premiums made every fixture's relative spread wide | Spread thresholds were left unchanged and the fixtures tightened their offsets, so the analyzer's real threshold is what the test exercises |
| An ATM band can legitimately be empty | The builder falls back to the nearest strike and the test asserts that behaviour instead of assuming a band hit |
| `BigDecimal` strike equality is scale-sensitive | Canonicalisation is applied both to duplicate detection and to `OptionLegKey`, so `500` and `500.00` are one leg |

---

## 15. Known Limitations

- Pricing uses a European Black-Scholes-Merton model with a continuous dividend yield. Early exercise, discrete dividends and American-style premium are not modelled.
- Implied volatility is solved from the last traded price; a stale or crossed last price can bias it. Mid-price solving is not implemented.
- The normal CDF approximation is accurate to about 1.5e-7, which is ample for descriptive analytics and below the noise of any real quote, but it is not a high-precision special-function implementation.
- Historical volatility history exists only in memory and only from the moment the service starts; IV rank and percentile are therefore unavailable until two captures exist, and are lost on restart.
- Max pain, PCR per expiry and expected move are computed for the nearest expiry where a single expiry is required; cross-expiry aggregation beyond PCR is not implemented.
- Open interest is used as a contract count without lot-size scaling, so pain values are premium points per lot.
- Support/resistance zones are the largest open-interest strikes; no volume profile, liquidity map or price-action confluence is applied.
- The chain provider is feed staging: instrument registration, contract master loading and quote fan-out are the caller's responsibility. No adapter populates it automatically yet.
- The service assesses on the market-assessment timestamp; when option quotes are newer than the spot candle, the capture uses the candle instant.
- One chain snapshot describes one instant; no rolling window of chain snapshots is retained beyond the single previous state used for change.
- Tradability is a liquidity band, not an execution estimate: no fill probability, impact model or order-book depth weighting is attempted.

---

## 16. Recommendations for Phase 8

Do not implement these here:

1. Treat option analytics as a consumer-facing description layer: a later engine should consume `OptionsAssessment` and `MarketAssessment`, never re-derive or modify them.
2. Add a feed adapter that populates `OptionChainProvider` from the broker option universe and quote stream, with contract-master refresh and expiry rollover as its own reviewed step.
3. Prefer mid-price implied volatility when both bid and ask exist, keeping last-price solving as the fallback; make the choice explicit in configuration.
4. Calibrate the ATM band, deep-moneyness thresholds and liquidity floors against real chain data before any downstream engine depends on them.
5. Decide whether max pain and PCR must aggregate across expiries, and add that as an explicit model change rather than a silent extension.
6. Keep Greeks model selection explicit and versioned: if a dividend-yield or American approximation is added, it must be visible in `GreeksAssessment.model`.
7. Keep persistence and scheduling out of analytics; if IV history must survive a restart, that is a storage phase with its own contract.
8. Build a recorded-chain replay fixture so future model changes can be compared deterministically against historical assessments.

---

## 17. Completion Gate

Phase 7 is implemented, tested and documented. The broker adapter, market domain, indicator engine, pipeline, calendar and market intelligence were not modified beyond the additive event kind required for publication. No strategies, recommendations, signals, entry/exit logic, risk, sizing, capital allocation, portfolio management, order execution, broker interaction, database, persistence, scheduler, notification, AI, ML or backtesting was started, and nothing was pushed or deployed.

**STOP — awaiting architectural review. The Strategy Engine has not been started.**
