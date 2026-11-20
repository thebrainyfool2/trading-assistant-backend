# Phase 14 — Deterministic Recommendation Engine

Status: **implemented and locally verified; not deployed.**

Phase 14 adds the first product-facing decision layer. It consumes the immutable
outputs of the existing engines and produces explainable `BUY CALL`, `BUY PUT`
or `NO TRADE` recommendations. It does not place, size, monitor, enter, exit or
execute a trade. It does not contain AI, ML, an LLM, a neural network, a
probabilistic black box or a call to an external model service.

## Architecture

```text
                    existing immutable engine outputs
 MarketAssessment ─┐
 Indicators ────────┤
 OptionsAssessment ─┤
 OptionChain ───────┤
 StrategyEvaluation ┤──> RecommendationContext ──> 21 independent scorers
 TradeQualification ┤                                      │
 RiskAssessment ────┤                                      ▼
 Candle/Freshness ──┘                          weighted score + confidence
                                                           │
                                                           ▼
                                              deterministic gates and edge
                                                           │
                                      ┌────────────────────┴──────────────┐
                                      ▼                                   ▼
                              BUY_CALL / BUY_PUT                     NO_TRADE
                                      │                                   │
                                      ▼                                   ▼
                           configured expiry/strike                no contract
                                      │
                                      ▼
                              Recommendation (+ events)
```

Dependency direction is one-way:

```text
existing engines ──> recommendation
recommendation ──X──> execution / broker adapters / Android / HTTP
```

No existing engine package imports `com.tradingassistant.recommendation`, and no
existing event contract was changed. The recommendation event hierarchy is
separate from the sealed `MarketEvent` hierarchy.

### Package layout

```text
com.tradingassistant.recommendation
├── api            RecommendationScorer
├── config         RecommendationConfig, RecommendationProperties,
│                  RecommendationConfiguration, StrikeSelectionPolicy,
│                  StrikeMode, ExpirySelection
├── domain         Recommendation, RecommendationKey, RecommendationBatch,
│                  RankedRecommendation, ComponentScore, ScorerResult, ScorerId,
│                  DirectionalBias, RecommendationType, OptionSelection,
│                  RecommendationContext
├── engine         RecommendationEngine, OptionSelector
├── event          RecommendationEvent, Generated/Updated/Expired events,
│                  RecommendationEventPublisher, RecommendationEventBus
├── exception      RecommendationException, RecommendationValidationException
├── registry       ScorerRegistry
├── rule           ScorerSupport
├── scorer         twenty-one scorer implementations
├── service        RecommendationService
└── validation     RecommendationValidator
```

## Inputs

The engine consumes only existing, already-computed values. It never calls an
indicator calculator, the options analytics engine, the risk calculator or a
broker.

| Input | Use |
|---|---|
| `MarketAssessment` | trend, momentum, volatility, participation, structure, session |
| indicator results | EMA/Supertrend/Donchian comparisons for alignment and breakout |
| `OptionsAssessment` | OI, OI changes, PCR, IV, IV rank, Greeks, expected move, liquidity, support/resistance |
| `OptionChain`/`ChainSnapshot` | actual listed expiry and strike selection |
| `StrategyEvaluation` | strategy agreement and diagnostics |
| `TradeQualification` | qualification gate and score |
| `RiskAssessment` | risk/reward score and risk gate |
| `Candle` | completed close used by a comparison, never a new calculation |
| `MarketDataFreshnessService.Freshness` | freshness and session gate/score |

`RecommendationContext` is immutable and rejects null collections. The live
service filters indicators, candles, strategy evaluations, qualifications and
risk assessments to the exact instrument, interval and market timestamp before
constructing it.

## Domain model

| Type | Kind | Purpose |
|---|---|---|
| `ScorerId` | enum | Stable identity of each required scorer |
| `DirectionalBias` | enum | `BULLISH`, `BEARISH`, `NEUTRAL`, `UNKNOWN` |
| `ScorerResult` | immutable record | score, maximum, bias, explanation and diagnostics from one scorer |
| `ComponentScore` | immutable record | scorer result plus configured weight and visible weighted contribution |
| `RecommendationType` | enum | only `BUY_CALL`, `BUY_PUT`, `NO_TRADE` |
| `OptionSelection` | immutable record | expiry, kind, strike, type, moneyness and selected policy; no entry price or size |
| `Recommendation` | immutable record | type, instrument, interval, timestamp, weighted score, confidence, components, optional contract, summary and diagnostics |
| `RecommendationKey` | immutable record | instrument, interval and market timestamp |
| `RankedRecommendation` | immutable record | one-based rank plus recommendation |
| `RecommendationBatch` | immutable record | copied list with deterministic `ranked()` and `top()` |
| `RecommendationContext` | immutable record | frozen engine outputs for one decision |

`Recommendation` validates that every scorer appears at most once, that a
`BUY_CALL` has a call selection, that a `BUY_PUT` has a put selection and that
`NO_TRADE` has no selection. It contains no mutable DTO and no shared state.

## Scoring model

Every scorer returns `score`, `maximum` (100), a directional bias, a
deterministic explanation and a diagnostics map. The weighted contribution is
visible:

```text
contribution = score / maximum × weight
weightedScore = Σ contribution
confidence = weightedScore / Σ weights × 100
```

Confidence is the weighted share of deterministic conditions that currently
support the result. It is not a probability and is not produced by a model.

| Scorer | Default weight | What it reads | Direction |
|---|---:|---|---|
| Trend | 20 | trend state, status and contributing indicators | trend |
| Momentum | 15 | strengthening/weakening and trend | trend when aligned |
| Volatility | 5 | stable/expanding/contracting | neutral |
| Liquidity | 10 | assessed/wide/thin legs and liquidity state | neutral |
| Volume | 5 | participation state | trend |
| Open interest | 10 | existing build-up classifications by strike | net call/put positioning |
| OI change | 5 | existing call/put OI changes | net change |
| PCR | 5 | chain OI put/call ratio against configured bands | ratio |
| IV | 5 | ATM IV and volatility state | neutral |
| IV rank | 5 | IV rank against configured cheap/expensive bands | neutral |
| Greeks | 5 | existing net delta exposure | delta sign |
| Expected move | 5 | expiry expected move versus existing stop distance | neutral |
| Support / resistance | 5 | existing OI zones and completed close | above/below zones |
| Breakout | 5 | structure breakout signal or existing Donchian values | breakout direction |
| Market structure | 5 | swing-structure bias | structure |
| Trend alignment | 10 | trend, structure, Supertrend direction, close versus EMA | majority direction |
| Risk / reward | 15 | achieved ratio against the risk engine’s minimum | neutral |
| Qualification | 10 | strongest existing qualification status | neutral |
| Strategy agreement | 5 | passed strategy evaluations | trend |
| Market session | 5 | existing session phase | neutral |
| Freshness | 5 | freshness status and session state | neutral |

The default weights total **160**, not 100. There is no second hardcoded total
to keep in sync: changing a configured weight changes the normalized confidence
automatically. A zero weight is allowed; a missing, negative or all-zero set is
rejected. Unknown, duplicate and missing scorer identities are rejected by the
registry.

The production defaults are in `app.recommendation.weights` in
`application.yml`; every weight has an environment override in `.env.example`.

## Decision rules

After scoring, the engine applies deterministic gates in this order:

1. `RecommendationValidator` rejects insufficient market data, stale data,
   mismatched instruments/timestamps, and missing aligned qualification.
2. A `QUALIFIED` trade qualification is required; otherwise `NO TRADE`.
3. A `RiskAssessment` with `WITHIN_LIMITS` is required; otherwise `NO TRADE`.
4. An options assessment and option chain are required; otherwise `NO TRADE`.
5. A closed market session produces `NO TRADE`.
6. The signed weighted edge is `bullishWeight − bearishWeight`. Its absolute
   value must be at least `minimum-directional-edge`; otherwise `NO TRADE`.
7. Confidence must be at least `minimum-confidence`; otherwise `NO TRADE`.
8. Positive edge selects `BUY_CALL`; negative edge selects `BUY_PUT`.
9. The configured option selector must find a supported contract. If it cannot,
   the result is `NO TRADE` (or an unsupported chain is rejected by validation).

A neutral market still has high-quality components (for example liquidity and
freshness), but no directional edge, so it produces `NO TRADE`. No point is
awarded without a component explanation.

## Option selection

Selection is entirely based on the captured chain and configuration:

- `allowed-expiry-kinds` filters weekly/monthly expiries.
- `expiry-selection` chooses `NEAREST`, `NEXT` or `FAR` among the allowed
  expiries.
- `strike-mode` chooses `ATM`, `ITM` or `OTM`.
- `strike-offset-steps` is the configured number of chain strike steps; the
  application does not contain fixed 100/200-point offsets.
- `maximum-strike-steps` bounds a configuration mistake.
- ATM uses the chain’s marked ATM group, falling back to the group nearest the
  captured spot.
- A call ITM moves down the strike ladder and a call OTM moves up it; a put uses
  the opposite direction.
- The selected leg is the actual `Strike` from the chain, so moneyness and
  expiry come from the feed, not from a new calculation.

The selection contains expiry, expiry kind, strike, option type and moneyness.
It deliberately contains no entry price, target, stop, exit, quantity or
position size.

## Ranking

`RecommendationBatch.ranked()` sorts by:

1. confidence descending;
2. weighted score descending;
3. recommendation type (call, put, no-trade);
4. segment name;
5. symbol token.

Rank is one-based and deterministic. There is no UI and no endpoint in Phase 14.

## Events

The existing sealed `MarketEvent` contract is unchanged. Recommendation uses a
separate hierarchy:

- `RecommendationGeneratedEvent` — first result for a key.
- `RecommendationUpdatedEvent` — a changed result for the same key.
- `RecommendationExpiredEvent` — session expiry or replacement by a newer market
  timestamp.

`RecommendationEventBus` is a small synchronous in-process publisher. The live
service stages inputs by `(instrument, interval, timestamp)` and publishes only
when market, options, qualification and risk evidence is present. It waits for
the options stage and chain rather than publishing a premature `NO TRADE` while
the pipeline is still completing the same timestamp. A stale or rejected
candidate is skipped and does not break the synchronous market event bus.

## Configuration

`RecommendationProperties` binds `app.recommendation` and converts once to the
immutable `RecommendationConfig`. Example:

```yaml
app:
  recommendation:
    enabled: true
    minimum-confidence: 55
    minimum-directional-edge: 5
    pcr-bullish-threshold: 1.20
    pcr-bearish-threshold: 0.80
    high-iv-rank: 80
    low-iv-rank: 20
    allowed-expiry-kinds: WEEKLY,MONTHLY
    expiry-selection: NEAREST
    strike-mode: ATM
    strike-offset-steps: 0
    maximum-strike-steps: 5
    weights:
      trend: 20
      momentum: 15
      volatility: 5
      liquidity: 10
      volume: 5
      open_interest: 10
      oi_change: 5
      pcr: 5
      iv: 5
      iv_rank: 5
      greeks: 5
      expected_move: 5
      support_resistance: 5
      breakout: 5
      market_structure: 5
      trend_alignment: 10
      risk_reward: 15
      qualification: 10
      strategy_agreement: 5
      market_session: 5
      freshness: 5
```

Validation rejects unknown scorer names, duplicate normalized names, missing
required scorers, negative weights, an all-zero weight set and out-of-range
thresholds. `RecommendationConfiguration` is conditional on the broker being
enabled, like the other engine layers.

## Validation

| Rejected condition | Where |
|---|---|
| Missing market assessment | `RecommendationContext` construction |
| Insufficient market data | `RecommendationValidator` |
| Stale or unknown freshness | `RecommendationValidator` |
| Missing aligned qualification | `RecommendationValidator` |
| Mismatched options instrument/timestamp | `RecommendationValidator` |
| Mismatched chain instrument/timestamp | `RecommendationValidator` / `OptionSelector` |
| Unsupported chain status | `OptionSelector` |
| No allowed expiry / unavailable expiry | `OptionSelector` |
| Strike outside the chain or missing leg | `OptionSelector` |
| Negative/missing/duplicate scorer weights | `RecommendationConfig`, `RecommendationProperties` |
| Duplicate/missing scorer IDs | `ScorerRegistry` |
| Invalid score range or blank explanation | `ScorerResult`, `ComponentScore`, `Recommendation` |
| Conflicting option selection/type | `Recommendation` |

## Tests

New tests:

- `RecommendationEngineTest` — bullish call, bearish put, neutral no-trade,
  disqualified qualification, missing options, stale data, missing qualification,
  unsupported expiry, confidence minimum, directional edge minimum, zero weight,
  mismatched options timestamp, deterministic equality and confidence arithmetic.
- `ScorerTest` — all 21 scorers return bounded explainable results; specific
  trend, momentum, options, structure, alignment, risk, qualification, strategy,
  session and freshness behavior.
- `RecommendationConfigTest` — valid policy, negative/missing/all-zero weights,
  invalid thresholds, strike policy rules, unknown/duplicate names and complete
  property conversion.
- `RecommendationConfigurationTest` — broker condition, Spring binding, all 21
  scorers, one market subscription and shutdown cleanup.
- `ScorerRegistryTest` — duplicate and missing scorer rejection.
- `OptionSelectorTest` — ATM, configured OTM/ITM calls and puts, unavailable
  expiry and no-trade selection.
- `RecommendationBatchTest` — deterministic ranking and empty batch.
- `RecommendationServiceTest` — out-of-order staging, generated event, duplicate
  suppression, updated event, expiry and stale-data skip.
- `ArchitectureGuardrailTest` — extended for recommendation dependencies,
  execution/broker/Android/HTTP imports, order placement and recalculation.

The full backend suite result after Phase 14 is **1,072 tests, 0 failures,
0 errors, 2 skipped**. The two skipped tests are the opt-in SmartAPI live
integration tests.

## Guardrails

`ArchitectureGuardrailTest` fails if:

- an AI/LLM/ML marker or dependency appears;
- recommendation imports execution or broker code, Android or an HTTP client;
- recommendation exposes a REST controller or order-placement call;
- recommendation imports indicator calculators, the options analytics engine,
  the risk engine or a Greeks calculator;
- recommendation is added to the randomness/wall-clock engine scan and violates
  it.

The guardrail is a source-level review aid, not a formal proof.

## Limitations

- **No live recommendation endpoint or UI.** Phase 14 is an engine capability
  only. Nothing displays or consumes the recommendations yet.
- **Live option-chain availability.** `InMemoryOptionChainProvider` is populated
  by the live options stage; if no chain is captured for the exact market
  timestamp, the service waits rather than fabricating strikes. This is a known
  live-data limitation inherited from the options layer.
- **No historical replay or backtesting.** Recommendations are evaluated from
  current staged events only.
- **No parameter optimisation.** Weights and thresholds are operator settings;
  Phase 14 provides no search, fitting or tuning loop.
- **No execution.** No entry, target, stop, exit, quantity, position size, order,
  broker call or automated action exists in this package.
- **Synthetic test inputs.** Automated tests use deterministic fixtures, not a
  live market session; the skipped SmartAPI tests were not run without
  credentials.
- **Confidence semantics.** Confidence is a weighted deterministic score share,
  not a probability or expected win rate.
- **Recommended contract only.** The engine reports a contract chosen from the
  supplied chain; it does not verify news, corporate actions, margins, brokerage
  or user suitability.

## Verification

```bash
cd backend
mvn -B -ntp test
```

The final full run is expected to report **1,072 tests with 0 failures, 0 errors
and 2 skipped** (BUILD SUCCESS). The skipped tests are the opt-in SmartAPI live
integration tests; they require broker credentials and were not run. No live
market session, broker order adapter or external AI service was used.
